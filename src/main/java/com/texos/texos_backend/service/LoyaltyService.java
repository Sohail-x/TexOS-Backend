package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.LoyaltyAccountOverview;
import com.texos.texos_backend.dto.LoyaltyOverviewReward;
import com.texos.texos_backend.dto.LoyaltyRedemptionSummary;
import com.texos.texos_backend.dto.LoyaltyTransactionOverview;
import com.texos.texos_backend.dto.LoyaltyTransactionSummary;
import com.texos.texos_backend.entity.*;
import com.texos.texos_backend.repository.*;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;
import com.texos.texos_backend.observability.DashboardRequestMeasurements.Measurement.Stage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import com.texos.texos_backend.config.AuthenticatedOwnerPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class LoyaltyService {
 private final LoyaltyProgramRepository programs;
 private final LoyaltyAccountRepository accounts;
 private final LoyaltyRewardRepository rewards;
 private final LoyaltyTransactionRepository transactions;
 private final LoyaltyRedemptionRepository redemptions;
 private final LoyaltyPointLotRepository pointLots;
 private final LoyaltyPointAllocationRepository allocations;
 private final LoyaltyAuditLogRepository auditLogs;
 private final RestaurantOrderRepository orderRepository;
 private final BillService billService;
 private final RestaurantRepository restaurants;
 private final OwnerRepository owners;
 private final CustomerRepository customers;
 private final RestaurantTableRepository tables;
 private final MenuItemRepository menuItems;
 private final CustomerSessionService sessions;

 private Restaurant ownedRestaurant(Long id) {
  long ownershipStartedAtNanos=DashboardRequestMeasurements.startStage();
  try {
   if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
       && attrs.getRequest().getAttribute(AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE) instanceof AuthenticatedOwnerPrincipal) {
    return restaurants.getReferenceById(id);
   }
   String principal=SecurityContextHolder.getContext().getAuthentication()==null?null:SecurityContextHolder.getContext().getAuthentication().getName();
   Owner owner=measureRepositoryOperation(Stage.SERVICE_OWNER_REPOSITORY,()->owners.findByEmail(principal==null?"":principal.trim().toLowerCase()).or(()->owners.findByPhone(principal)).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED)));
   return measureRepositoryOperation(Stage.SERVICE_RESTAURANT_REPOSITORY,()->restaurants.findByIdAndOwnerId(id,owner.getId()).orElseThrow(()->new ResourceNotFoundException("Restaurant not found")));
  } finally {
   DashboardRequestMeasurements.recordStageDuration(
       Stage.SERVICE_OWNERSHIP_VALIDATION,ownershipStartedAtNanos);
  }
 }
 private RestaurantTable table(String qr) {
  return tables.findByQrTokenAndActiveTrue(qr).orElseThrow(()->new ResourceNotFoundException("Invalid QR code"));
 }
 private Customer customer(HttpServletRequest req,Long restaurantId) {
  CustomerSession session=sessions.getActiveSession(req,restaurantId);
  if(session==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Please log in to access rewards");
  return customers.findByIdAndRestaurant_IdForUpdate(session.getCustomerId(),restaurantId).orElseThrow(()->new ResourceNotFoundException("Customer not found"));
 }
 private LoyaltyProgram program(Restaurant r) {
  Optional<LoyaltyProgram> existing=measureRepositoryOperation(Stage.LOYALTY_PROGRAM_REPOSITORY,()->programs.findByRestaurant_Id(r.getId()));
  if(existing.isPresent())return existing.get();
  LoyaltyProgram p=new LoyaltyProgram();p.setRestaurant(r);
  return measureRepositoryOperation(Stage.LOYALTY_PROGRAM_SAVE,()->programs.save(p));
 }
 @Transactional
 public Map<String,Object> customerStatus(String qr,HttpServletRequest req,String from,String to) {
  return customerStatus(qr,req,from,to,null,null);
 }
 @Transactional
 public Map<String,Object> customerStatus(String qr,HttpServletRequest req,String from,String to,
      Integer historyPage,Integer historySize) {
  Restaurant r=table(qr).getRestaurant(); Map<String,Object> out=new LinkedHashMap<>();
  LoyaltyProgram p=programs.findByRestaurant_Id(r.getId()).orElse(null);
  out.put("enabled",p!=null&&p.isEnabled());out.put("restaurantName",r.getRestaurantName());
  if(p!=null){out.put("conversionPoints",p.getConversionPoints());out.put("conversionInr",p.getConversionInr());}
  if(p!=null){out.put("minimumRedemptionPoints",p.getMinimumRedemptionPoints());}
  CustomerSession cs=sessions.getActiveSession(req,r.getId()); out.put("authenticated",cs!=null);
  if(cs!=null){Customer c=customers.findByIdAndRestaurant_Id(cs.getCustomerId(),r.getId()).orElseThrow(()->new ResourceNotFoundException("Customer not found"));
   LoyaltyAccount a=accounts.findByRestaurant_IdAndCustomer_Id(r.getId(),c.getId()).orElse(null);
   if(a!=null) expireAvailableLots(a);
   out.put("customerName",c.getCustomerName());out.put("mobileNumber",c.getMobileNumber());out.put("availablePoints",a==null?0:a.getAvailablePoints());
   out.put("lifetimeEarnedPoints",a==null?0:a.getLifetimeEarnedPoints());out.put("lifetimeRedeemedPoints",a==null?0:a.getLifetimeRedeemedPoints());out.put("pointsDebt",a==null?0:a.getPointsDebt());
   CustomerHistory history=customerHistory(r.getId(),c.getId(),from,to,historyPage,historySize);
   out.put("transactions",history.transactions().stream().map(this::txView).toList());
   if(historyPage!=null||historySize!=null)out.put("transactionsHasMore",history.hasMore());
  }
  out.put("rewards",rewards.findAllByRestaurant_IdAndActiveTrueAndDeletedFalseOrderByCreatedAtDesc(r.getId()).stream().filter(this::available).map(this::rewardView).toList());
  return out;
 }
 private CustomerHistory customerHistory(Long restaurantId,Long customerId,String from,String to,
      Integer historyPage,Integer historySize) {
  boolean paginated=historyPage!=null||historySize!=null;
  if(paginated&&(historyPage==null||historySize==null||historyPage<0||historySize<1||historySize>100))
   throw new IllegalArgumentException("History page must be non-negative and page size must be between 1 and 100");
  if(from==null&&to==null) {
   if(paginated) {
    int pageSize=java.util.Objects.requireNonNull(historySize);
    List<LoyaltyTransactionSummary> result=transactions.findCustomerSummaries(
        restaurantId,customerId,PageRequest.of(java.util.Objects.requireNonNull(historyPage),pageSize+1));
    boolean hasMore=result.size()>pageSize;
    if(hasMore)result=result.subList(0,pageSize);
    return new CustomerHistory(result,hasMore);
   }
   return new CustomerHistory(transactions.findCustomerSummaries(restaurantId,customerId,PageRequest.of(0,100)),false);
  }
  if(from==null||to==null)throw new IllegalArgumentException("Both history dates are required");
  java.time.LocalDate start=java.time.LocalDate.parse(from);
  java.time.LocalDate end=java.time.LocalDate.parse(to);
  if(end.isBefore(start))throw new IllegalArgumentException("History end date must not be before start date");
  LocalDateTime fromTime=start.atStartOfDay();
  LocalDateTime toTime=end.plusDays(1).atStartOfDay().minusNanos(1);
  if(!paginated)
   return new CustomerHistory(transactions.findCustomerSummariesBetween(restaurantId,customerId,fromTime,toTime),false);
    int pageSize=java.util.Objects.requireNonNull(historySize);
  List<LoyaltyTransactionSummary> result=transactions.findCustomerSummariesBetween(
            restaurantId,customerId,fromTime,toTime,PageRequest.of(java.util.Objects.requireNonNull(historyPage),pageSize+1));
  boolean hasMore=result.size()>pageSize;
  if(hasMore)result=result.subList(0,pageSize);
  return new CustomerHistory(result,hasMore);
 }
 @Transactional
 public Map<String,Object> customerBalance(String qr,HttpServletRequest req) {
  Restaurant r=table(qr).getRestaurant(); Customer c=customer(req,r.getId());
  LoyaltyAccount a=accounts.findByRestaurant_IdAndCustomer_Id(r.getId(),c.getId()).orElse(null);
  if(a!=null) expireAvailableLots(a);
  Map<String,Object> m=new LinkedHashMap<>();m.put("availablePoints",a==null?0:a.getAvailablePoints());m.put("lifetimeEarnedPoints",a==null?0:a.getLifetimeEarnedPoints());m.put("lifetimeRedeemedPoints",a==null?0:a.getLifetimeRedeemedPoints());m.put("pointsDebt",a==null?0:a.getPointsDebt());
  LoyaltyProgram p=program(r);m.put("conversionPoints",p.getConversionPoints());m.put("conversionInr",p.getConversionInr());
  m.put("minimumRedemptionPoints",p.getMinimumRedemptionPoints());
  m.put("transactions",transactions.findCustomerSummaries(r.getId(),c.getId(),PageRequest.of(0,100)).stream().map(this::txView).toList());return m;
 }
 @Transactional
 public Map<String,Object> ownerSettings(Long id) {
  Restaurant r=ownedRestaurant(id);LoyaltyProgram loyaltyProgram=program(r);
  long mappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  Map<String,Object> result=settingsView(loyaltyProgram);
  DashboardRequestMeasurements.recordStageDuration(Stage.OWNER_SETTINGS_MAPPING,mappingStartedAtNanos);
  DashboardRequestMeasurements.recordOwnerResponseCounts(1,0,0,0,0);
  return result;
 }
 @Transactional
 public Map<String,Object> updateSettings(Long id,Map<String,Object> body) {
  Restaurant r=ownedRestaurant(id); LoyaltyProgram p=program(r);
  boolean enabled=Boolean.TRUE.equals(body.get("enabled"));
  int rate=num(body.get("pointsPer100"),p.getPointsPer100()); int minPoints=num(body.get("minimumRedemptionPoints"),p.getMinimumRedemptionPoints());
  BigDecimal minAmount=decimal(body.get("minimumOrderAmount"),p.getMinimumOrderAmount());
  Integer expiry=body.get("pointsExpiryDays")==null?null:num(body.get("pointsExpiryDays"),0);
  int conversionPoints=num(body.get("conversionPoints"),p.getConversionPoints());
  BigDecimal conversionInr=decimal(body.get("conversionInr"),p.getConversionInr());
  if(rate<1||rate>10000||minPoints<1||minAmount.signum()<0||(expiry!=null&&(expiry<1||expiry>3650))
      ||conversionPoints<1||conversionInr.signum()<=0) throw new IllegalArgumentException("Invalid loyalty settings");
  p.setEnabled(enabled);p.setPointsPer100(rate);p.setMinimumRedemptionPoints(minPoints);p.setMinimumOrderAmount(minAmount);p.setPointsExpiryDays(expiry);
  p.setConversionPoints(conversionPoints);p.setConversionInr(conversionInr.setScale(2,RoundingMode.HALF_UP));
  LoyaltyProgram saved=measureRepositoryOperation(Stage.OWNER_SETTINGS_SAVE,()->programs.save(p));
  audit(r,"SETTINGS_UPDATED","LOYALTY_PROGRAM",saved.getId(),"enabled="+enabled+", pointsPer100="+rate+", minimumOrderAmount="+minAmount+", expiryDays="+expiry);
  long mappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  Map<String,Object> result=settingsView(saved);
  DashboardRequestMeasurements.recordStageDuration(Stage.OWNER_SETTINGS_MAPPING,mappingStartedAtNanos);
  DashboardRequestMeasurements.recordOwnerResponseCounts(1,0,0,0,0);
  return result;
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> ownerRewards(Long id) {
  ownedRestaurant(id);
  List<LoyaltyReward> rewardEntities=measureRepositoryOperation(
      Stage.OWNER_REWARDS_LIST,
      ()->rewards.findAllByRestaurant_IdAndDeletedFalseOrderByCreatedAtDesc(id));
  DashboardRequestMeasurements.recordRepositoryResultCount(
      Stage.OWNER_REWARDS_LIST,rewardEntities.size());
  long mappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  List<Map<String,Object>> result=rewardEntities.stream().map(this::rewardView).toList();
  DashboardRequestMeasurements.recordStageDuration(Stage.OWNER_REWARD_MAPPING,mappingStartedAtNanos);
  DashboardRequestMeasurements.recordOwnerResponseCounts(result.size(),result.size(),0,0,0);
  recordRewardImagePayload(rewardEntities);
  return result;
 }
 @Transactional
 public Map<String,Object> saveReward(Long id,Long rewardId,Map<String,Object> body) {
  Restaurant r=ownedRestaurant(id); LoyaltyReward x;
  if(rewardId==null){x=new LoyaltyReward();x.setRestaurant(r);x.setRedemptionCount(0);}
  else x=measureRepositoryOperation(Stage.OWNER_REWARD_LOCK,()->rewards.lockReward(rewardId,id).orElseThrow(()->new ResourceNotFoundException("Reward not found")));
  String name=str(body.get("rewardName"));String type=str(body.get("rewardType")).toUpperCase(Locale.ROOT);
  int points=num(body.get("pointsRequired"),0);
  if(name.isBlank()||points<1||!Set.of("FREE_ITEM","PERCENTAGE_DISCOUNT","FIXED_DISCOUNT").contains(type)) throw new IllegalArgumentException("Enter a reward name, valid reward type, and positive points");
  String image=str(body.get("imageUrl"));
  if(image.length()>3_000_000 || (!image.isBlank()
      && !(image.matches("(?is)^data:image/(png|jpeg|webp);base64,[a-z0-9+/=]+$")
           || image.matches("(?i)^https://[^\\s]+$"))))
   throw new IllegalArgumentException("Reward image must be a PNG, JPEG, or WebP image under 2 MB, or a trusted HTTPS image URL");
  x.setRewardName(name);x.setDescription(str(body.get("description")));x.setImageUrl(image);x.setRewardType(type);x.setPointsRequired(points);
  x.setMinimumOrderAmount(nullableDecimal(body.get("minimumOrderAmount")));
  x.setDiscountPercentage(nullableDecimal(body.get("discountPercentage")));x.setDiscountAmount(nullableDecimal(body.get("discountAmount")));x.setMaximumDiscount(nullableDecimal(body.get("maximumDiscount")));
  x.setTotalQuantity(nullableInt(body.get("totalQuantity")));x.setRemainingQuantity(nullableInt(body.get("remainingQuantity")));
  x.setMaxRedemptions(nullableInt(body.get("maxRedemptions")));x.setStartDate(date(body.get("startDate")));x.setExpiryDate(date(body.get("expiryDate")));
  if(type.equals("FREE_ITEM")) {Long menuId=nullableLong(body.get("menuItemId"));MenuItem item=menuId==null?null:measureRepositoryOperation(Stage.OWNER_MENU_ITEM_LOOKUP,()->menuItems.findById(menuId).orElseThrow(()->new ResourceNotFoundException("Menu item not found")));if(item==null||!item.isAvailable()||!item.getCategory().getRestaurant().getId().equals(id))throw new IllegalArgumentException("Select an available menu item from this restaurant");x.setMenuItem(item);}
  if(type.equals("PERCENTAGE_DISCOUNT")&&(x.getDiscountPercentage()==null||x.getDiscountPercentage().signum()<=0||x.getDiscountPercentage().compareTo(new BigDecimal("100"))>0))throw new IllegalArgumentException("Discount percentage must be between 0 and 100");
  if(type.equals("FIXED_DISCOUNT")&&(x.getDiscountAmount()==null||x.getDiscountAmount().signum()<=0))throw new IllegalArgumentException("Enter a valid fixed discount amount");
  if(x.getTotalQuantity()!=null&&x.getTotalQuantity()<1)throw new IllegalArgumentException("Quantity must be positive");
  if(x.getRemainingQuantity()!=null&&(x.getRemainingQuantity()<0 || (x.getTotalQuantity()!=null&&x.getRemainingQuantity()>x.getTotalQuantity())))throw new IllegalArgumentException("Remaining quantity must be between zero and total quantity");
  if(x.getMaxRedemptions()!=null&&x.getMaxRedemptions()<1)throw new IllegalArgumentException("Maximum redemptions must be positive");
  if(x.getMinimumOrderAmount()!=null&&x.getMinimumOrderAmount().signum()<0)throw new IllegalArgumentException("Minimum order amount cannot be negative");
  if(x.getStartDate()!=null&&x.getExpiryDate()!=null&&!x.getExpiryDate().isAfter(x.getStartDate()))throw new IllegalArgumentException("Expiry date must be after the start date");
  x.setActive(body.get("active")==null||Boolean.TRUE.equals(body.get("active")));
  LoyaltyReward saved=measureRepositoryOperation(Stage.OWNER_REWARD_SAVE,()->rewards.save(x));
  audit(r,rewardId==null?"REWARD_CREATED":"REWARD_UPDATED","REWARD",saved.getId(),saved.getRewardName()+" ("+saved.getRewardType()+")");
  long mappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  Map<String,Object> result=rewardView(saved);
  DashboardRequestMeasurements.recordStageDuration(Stage.OWNER_REWARD_MAPPING,mappingStartedAtNanos);
  DashboardRequestMeasurements.recordOwnerResponseCounts(1,1,0,0,0);
  recordRewardImagePayload(List.of(saved));
  return result;
 }
 @Transactional
 public Map<String,Object> setRewardStatus(Long id,Long rewardId,boolean active){
  Restaurant restaurant=ownedRestaurant(id);
  LoyaltyReward r=measureRepositoryOperation(Stage.OWNER_REWARD_LOCK,()->rewards.lockReward(rewardId,id).orElseThrow(()->new ResourceNotFoundException("Reward not found")));
  r.setActive(active);audit(restaurant,active?"REWARD_ACTIVATED":"REWARD_DEACTIVATED","REWARD",rewardId,r.getRewardName());
  long mappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  Map<String,Object> result=rewardView(r);
  DashboardRequestMeasurements.recordStageDuration(Stage.OWNER_REWARD_MAPPING,mappingStartedAtNanos);
  DashboardRequestMeasurements.recordOwnerResponseCounts(1,1,0,0,0);
  recordRewardImagePayload(List.of(r));
  return result;
 }
 @Transactional
 public void deleteReward(Long id,Long rewardId){Restaurant restaurant=ownedRestaurant(id);LoyaltyReward r=measureRepositoryOperation(Stage.OWNER_REWARD_LOCK,()->rewards.lockReward(rewardId,id).orElseThrow(()->new ResourceNotFoundException("Reward not found")));r.setActive(false);r.setDeleted(true);audit(restaurant,"REWARD_ARCHIVED","REWARD",rewardId,r.getRewardName());}
 @Transactional(readOnly=true)
 public Map<String,Object> overview(Long id) {
  ownedRestaurant(id);Map<String,Object> m=new LinkedHashMap<>();
  LoyaltyAccountOverview accountTotals=measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_ACCOUNT_AGGREGATE,()->accounts.overviewByRestaurant(id));
  LoyaltyTransactionOverview transactionTotals=measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_TRANSACTION_AGGREGATE,()->transactions.overviewByRestaurant(id));
  long mappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  m.put("totalLoyaltyMembers",accountTotals.totalMembers());
  m.put("totalPointsIssued",transactionTotals.totalPointsIssued());
  m.put("totalPointsRedeemed",transactionTotals.totalPointsRedeemed());
  m.put("availablePointsOutstanding",accountTotals.availablePointsOutstanding());
  DashboardRequestMeasurements.recordStageDuration(Stage.OWNER_OVERVIEW_MAPPING,mappingStartedAtNanos);
  m.put("totalRewardsCreated",measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_REWARD_COUNT,()->rewards.countByRestaurant_IdAndDeletedFalse(id)));
  m.put("totalSuccessfulRedemptions",measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_REDEMPTION_COUNT,()->redemptions.countByRestaurant_IdAndStatus(id,"REDEEMED")));
  List<LoyaltyRedemptionSummary> recentRedemptions=measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_RECENT_REDEMPTIONS,
      ()->redemptions.findRecentSummariesByRestaurant(id,PageRequest.of(0,100)));
  DashboardRequestMeasurements.recordRepositoryResultCount(
      Stage.OWNER_OVERVIEW_RECENT_REDEMPTIONS,recentRedemptions.size());
  long recentRedemptionMappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  List<Map<String,Object>> recentRedemptionViews=recentRedemptions.stream()
      .limit(10).map(this::redemptionView).toList();
  DashboardRequestMeasurements.recordStageDuration(
      Stage.OWNER_REDEMPTION_MAPPING,recentRedemptionMappingStartedAtNanos);
  m.put("recentRedemptions",recentRedemptionViews);
  List<LoyaltyTransactionSummary> recentTransactions=measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_RECENT_TRANSACTIONS,
      ()->transactions.findRecentSummariesByRestaurant(id,PageRequest.of(0,20)));
  DashboardRequestMeasurements.recordRepositoryResultCount(
      Stage.OWNER_OVERVIEW_RECENT_TRANSACTIONS,recentTransactions.size());
  long recentTransactionMappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  List<Map<String,Object>> recentTransactionViews=recentTransactions.stream()
      .map(this::txView).toList();
  DashboardRequestMeasurements.recordStageDuration(
      Stage.OWNER_OVERVIEW_MAPPING,recentTransactionMappingStartedAtNanos);
  m.put("recentTransactions",recentTransactionViews);
  Map<Long,Long> popular=new LinkedHashMap<>();
  for(LoyaltyRedemptionSummary redemption:recentRedemptions) {
   if(redemption.rewardId()!=null) popular.merge(redemption.rewardId(),1L,Long::sum);
  }
  List<LoyaltyOverviewReward> overviewRewards=measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_REWARDS,()->rewards.findOverviewRewardsByRestaurant(id));
  DashboardRequestMeasurements.recordRepositoryResultCount(
      Stage.OWNER_OVERVIEW_REWARDS,overviewRewards.size());
  long popularRewardMappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  List<Map<String,Object>> popularRewards=overviewRewards.stream()
    .map(reward->overviewRewardView(reward,popular.getOrDefault(reward.id(),0L)))
    .sorted((a,b)->Long.compare(((Number)b.get("redemptions")).longValue(),((Number)a.get("redemptions")).longValue())).limit(5).toList();
  DashboardRequestMeasurements.recordStageDuration(
      Stage.OWNER_OVERVIEW_MAPPING,popularRewardMappingStartedAtNanos);
  m.put("popularRewards",popularRewards);
  List<LoyaltyTransactionSummary> earnedTransactions=measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_EARNED_TRANSACTIONS,
      ()->transactions.findRecentSummariesByRestaurantAndType(id,"EARN",PageRequest.of(0,8)));
  DashboardRequestMeasurements.recordRepositoryResultCount(
      Stage.OWNER_OVERVIEW_EARNED_TRANSACTIONS,earnedTransactions.size());
  long earnedMappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  List<Map<String,Object>> earnedTransactionViews=earnedTransactions.stream()
      .map(this::txView).toList();
  DashboardRequestMeasurements.recordStageDuration(
      Stage.OWNER_OVERVIEW_MAPPING,earnedMappingStartedAtNanos);
  m.put("pointsEarnedOverTime",earnedTransactionViews);
  List<LoyaltyTransactionSummary> redeemedTransactions=measureRepositoryOperation(
      Stage.OWNER_OVERVIEW_REDEEMED_TRANSACTIONS,
      ()->transactions.findRecentSummariesByRestaurantAndType(id,"REDEEM",PageRequest.of(0,8)));
  DashboardRequestMeasurements.recordRepositoryResultCount(
      Stage.OWNER_OVERVIEW_REDEEMED_TRANSACTIONS,redeemedTransactions.size());
  long redeemedMappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  List<Map<String,Object>> redeemedTransactionViews=redeemedTransactions.stream()
      .map(this::txView).toList();
  DashboardRequestMeasurements.recordStageDuration(
      Stage.OWNER_OVERVIEW_MAPPING,redeemedMappingStartedAtNanos);
  m.put("pointsRedeemedOverTime",redeemedTransactionViews);
  DashboardRequestMeasurements.recordOwnerResponseCounts(
      recentRedemptionViews.size()+recentTransactionViews.size()+popularRewards.size()
          +earnedTransactionViews.size()+redeemedTransactionViews.size(),
      popularRewards.size(),recentRedemptionViews.size(),0,
      recentTransactionViews.size()+earnedTransactionViews.size()+redeemedTransactionViews.size());
  DashboardRequestMeasurements.recordRewardImagePayload(0,0,0);
  return m;
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> ownerRedemptions(Long id){
  ownedRestaurant(id);
  List<LoyaltyRedemptionSummary> entities=measureRepositoryOperation(
      Stage.OWNER_REDEMPTIONS_LIST,
      ()->redemptions.findRecentSummariesByRestaurant(id,PageRequest.of(0,100)));
  DashboardRequestMeasurements.recordRepositoryResultCount(
      Stage.OWNER_REDEMPTIONS_LIST,entities.size());
  long mappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  List<Map<String,Object>> result=entities.stream().map(this::redemptionView).toList();
  DashboardRequestMeasurements.recordStageDuration(
      Stage.OWNER_REDEMPTION_MAPPING,mappingStartedAtNanos);
  DashboardRequestMeasurements.recordOwnerResponseCounts(
      result.size(),0,result.size(),0,0);
  DashboardRequestMeasurements.recordRewardImagePayload(0,0,0);
  return result;
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> ownerAuditLogs(Long id){
  ownedRestaurant(id);
  List<LoyaltyAuditLog> entities=measureRepositoryOperation(
      Stage.OWNER_AUDIT_LOGS_LIST,
      ()->auditLogs.findTop100ByRestaurant_IdOrderByCreatedAtDesc(id));
  DashboardRequestMeasurements.recordRepositoryResultCount(
      Stage.OWNER_AUDIT_LOGS_LIST,entities.size());
  long mappingStartedAtNanos=DashboardRequestMeasurements.startStage();
  List<Map<String,Object>> result=entities.stream().map(log->{
   Map<String,Object> row=new LinkedHashMap<>();row.put("id",log.getId());
   row.put("actor",log.getActorIdentity());row.put("action",log.getAction());
   row.put("targetType",log.getTargetType());row.put("targetId",log.getTargetId());
   row.put("details",log.getDetails());row.put("createdAt",log.getCreatedAt());return row;
  }).toList();
  DashboardRequestMeasurements.recordStageDuration(Stage.OWNER_AUDIT_MAPPING,mappingStartedAtNanos);
  DashboardRequestMeasurements.recordOwnerResponseCounts(
      result.size(),0,0,result.size(),0);
  DashboardRequestMeasurements.recordRewardImagePayload(0,0,0);
  return result;
 }

 @Transactional
 public void applyRewardToOrder(Long rewardId,Customer customer,Restaurant restaurant,RestaurantOrder order,BigDecimal eligibleSubtotal) {
  if(rewardId==null)return;
  LoyaltyProgram p=programs.findByRestaurant_Id(restaurant.getId()).orElseThrow(()->new IllegalArgumentException("Loyalty program is not enabled"));
  if(!p.isEnabled())throw new IllegalArgumentException("Loyalty program is disabled");
  LoyaltyReward r=rewards.lockReward(rewardId,restaurant.getId()).orElseThrow(()->new ResourceNotFoundException("Reward not found"));
  if(!available(r)||eligibleSubtotal.compareTo(p.getMinimumOrderAmount())<0||(r.getMinimumOrderAmount()!=null&&eligibleSubtotal.compareTo(r.getMinimumOrderAmount())<0))throw new IllegalArgumentException("Reward is not eligible for this order");
  LoyaltyAccount a=accounts.lockAccount(restaurant.getId(),customer.getId()).orElseThrow(()->new IllegalArgumentException("No loyalty points available"));
  expireAvailableLots(a);
  if(a.getAvailablePoints()<Math.max(p.getMinimumRedemptionPoints(),r.getPointsRequired()))throw new IllegalArgumentException("Insufficient loyalty points");
  if(r.getRemainingQuantity()!=null&&r.getRemainingQuantity()<1)throw new IllegalArgumentException("Reward is out of stock");
  if(r.getMaxRedemptions()!=null&&r.getRedemptionCount()>=r.getMaxRedemptions())throw new IllegalArgumentException("Reward redemption limit reached");
  BigDecimal discount=BigDecimal.ZERO;
  if("PERCENTAGE_DISCOUNT".equals(r.getRewardType())) {discount=eligibleSubtotal.multiply(r.getDiscountPercentage()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);if(r.getMaximumDiscount()!=null)discount=discount.min(r.getMaximumDiscount());}
  if("FIXED_DISCOUNT".equals(r.getRewardType())) discount=r.getDiscountAmount().min(eligibleSubtotal);
  if("FREE_ITEM".equals(r.getRewardType())) {MenuItem mi=r.getMenuItem();if(mi==null||!mi.isAvailable()||mi.getCategory()==null||!mi.getCategory().isActive()||!mi.getCategory().getRestaurant().getId().equals(restaurant.getId()))throw new IllegalArgumentException("The reward item is no longer available");OrderItem free=new OrderItem();free.setOrder(order);free.setMenuItem(mi);free.setItemName(mi.getItemName()+" (Reward)");free.setUnitPrice(BigDecimal.ZERO);free.setQuantity(1);free.setLineTotal(BigDecimal.ZERO);order.getItems().add(free);}
  a.setAvailablePoints(a.getAvailablePoints()-r.getPointsRequired());accounts.save(a);
  if(r.getRemainingQuantity()!=null)r.setRemainingQuantity(r.getRemainingQuantity()-1);r.setRedemptionCount(r.getRedemptionCount()+1);
  LoyaltyRedemption red=new LoyaltyRedemption();red.setRestaurant(restaurant);red.setCustomer(customer);red.setReward(r);red.setOrder(order);red.setPointsUsed(r.getPointsRequired());red.setDiscountValue(discount);red.setStatus("PENDING");red.setExpiresAt(LocalDateTime.now().plusHours(2));red.setRedemptionCode("TXR-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT));redemptions.save(red);
  reservePointLots(a,red,r.getPointsRequired());
  order.setRewardDiscountAmount(discount);
  saveTx(restaurant,customer,order,red,"RESERVATION",r.getPointsRequired(),"Points reserved for checkout","reserve:"+order.getOrderNumber());
 }
 @Transactional
 public void applyPointConversionToOrder(Integer points,Customer customer,Restaurant restaurant,RestaurantOrder order,BigDecimal eligibleSubtotal) {
  if(points==null)return;
  LoyaltyProgram p=programs.findByRestaurant_Id(restaurant.getId()).orElseThrow(()->new IllegalArgumentException("Loyalty program is not enabled"));
  if(!p.isEnabled())throw new IllegalArgumentException("Loyalty program is disabled");
  if(points<1)throw new IllegalArgumentException("Enter a positive number of points");
  if(points<p.getMinimumRedemptionPoints())throw new IllegalArgumentException("Minimum redemption is "+p.getMinimumRedemptionPoints()+" points");
  LoyaltyAccount a=accounts.lockAccount(restaurant.getId(),customer.getId()).orElseThrow(()->new IllegalArgumentException("No loyalty points available"));
  expireAvailableLots(a);
  if(a.getAvailablePoints()<points)throw new IllegalArgumentException("Insufficient loyalty points");

  int maxRedeemablePoints = eligibleSubtotal
      .multiply(BigDecimal.valueOf(p.getConversionPoints()))
      .divide(p.getConversionInr(), 0, RoundingMode.FLOOR)
      .intValue();
  if (maxRedeemablePoints < 1) {
   throw new InvalidRequestException("Order amount is too low to redeem loyalty points");
  }
  if (points > maxRedeemablePoints) {
   throw new InvalidRequestException("You can redeem a maximum of " + maxRedeemablePoints + " points for this order");
  }


  BigDecimal discount=p.getConversionInr()
      .multiply(BigDecimal.valueOf(points))
      .divide(BigDecimal.valueOf(p.getConversionPoints()),2,RoundingMode.HALF_UP);
  discount=discount.min(eligibleSubtotal).setScale(2,RoundingMode.HALF_UP);
  if(discount.signum()<0)throw new IllegalArgumentException("Invalid reward discount");
  a.setAvailablePoints(a.getAvailablePoints()-points);accounts.save(a);
  LoyaltyRedemption red=new LoyaltyRedemption();
  red.setRestaurant(restaurant);red.setCustomer(customer);red.setReward(null);red.setOrder(order);
  red.setPointsUsed(points);red.setDiscountValue(discount);red.setStatus("PENDING");
  red.setExpiresAt(LocalDateTime.now().plusHours(2));
  red.setRedemptionCode("TXR-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT));
  redemptions.save(red);
  reservePointLots(a,red,points);
  order.setRewardDiscountAmount(discount);


  saveTx(restaurant,customer,order,red,"RESERVATION",points,"Reward points reserved for checkout","reserve:"+order.getOrderNumber());
 }
 @Transactional
 public void processOrderLifecycle(RestaurantOrder order) {
  if(order.getCustomer()==null)return;
  List<LoyaltyRedemption> list=redemptions.findAllByOrder_IdAndStatus(order.getId(),"PENDING");
  for(LoyaltyRedemption red:list) {
   if(order.getPaymentStatus()==PaymentStatus.PAID) {
    red.setStatus("REDEEMED");red.setRedeemedAt(LocalDateTime.now());redemptions.save(red);
    LoyaltyAccount a=accounts.lockAccount(order.getRestaurant().getId(),order.getCustomer().getId()).orElseThrow();a.setLifetimeRedeemedPoints(a.getLifetimeRedeemedPoints()+red.getPointsUsed());accounts.save(a);
    finalizePointAllocations(red);
    saveTx(order.getRestaurant(),order.getCustomer(),order,red,"REDEEM",red.getPointsUsed(),"Reward redeemed","redeem:"+red.getRedemptionCode());
   } else if(order.getPaymentStatus()==PaymentStatus.FAILED||order.getPaymentStatus()==PaymentStatus.REFUNDED||order.getOrderStatus()==OrderStatus.CANCELLED) {
    LoyaltyAccount a=accounts.lockAccount(order.getRestaurant().getId(),order.getCustomer().getId()).orElseThrow();
    releasePointAllocations(red, a);
    accounts.save(a);
    LoyaltyReward r=red.getReward();
    if(r!=null){if(r.getRemainingQuantity()!=null)r.setRemainingQuantity(r.getRemainingQuantity()+1);r.setRedemptionCount(Math.max(0,r.getRedemptionCount()-1));}
    red.setStatus("CANCELLED");redemptions.save(red);
    saveTx(order.getRestaurant(),order.getCustomer(),order,red,"RELEASE",red.getPointsUsed(),"Reserved points released","release:"+red.getRedemptionCode());
   }
  }
  if(order.getPaymentStatus()==PaymentStatus.REFUNDED) {
   restoreRedeemedRedemptionsForRefund(order);
   reverseEarnedPointsForRefund(order);
  }
  if(order.getCustomer()!=null&&order.getPaymentStatus()==PaymentStatus.PAID) awardPoints(order);
 }



 private void awardPoints(RestaurantOrder o) {
  LoyaltyProgram p=programs.findByRestaurant_Id(o.getRestaurant().getId()).orElse(null);if(p==null||!p.isEnabled())return;
  String key="earn:order:"+o.getId();if(transactions.existsByIdempotencyKey(key))return;
  BigDecimal subtotal=o.getItems().stream().filter(i->!i.getItemName().endsWith("(Reward)")).map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
    if (o.getTotalAmount() != null && o.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
        subtotal = subtotal.min(o.getTotalAmount());
    }
  if(subtotal.compareTo(p.getMinimumOrderAmount())<0)return;



  long points=subtotal.multiply(BigDecimal.valueOf(p.getPointsPer100())).divide(new BigDecimal("100"),0,RoundingMode.FLOOR).longValue();if(points<1)return;
  LoyaltyAccount a=accounts.lockAccount(o.getRestaurant().getId(),o.getCustomer().getId()).orElseGet(()->{LoyaltyAccount n=new LoyaltyAccount();n.setRestaurant(o.getRestaurant());n.setCustomer(o.getCustomer());return accounts.save(n);});
  long debtPaid=Math.min(points,Math.max(0,a.getPointsDebt()));
  long netPoints=points-debtPaid;
  a.setPointsDebt(a.getPointsDebt()-debtPaid);
  a.setAvailablePoints(a.getAvailablePoints()+netPoints);
  a.setLifetimeEarnedPoints(a.getLifetimeEarnedPoints()+points);
  accounts.save(a);
  if(netPoints>0) {
   LoyaltyPointLot lot=new LoyaltyPointLot();lot.setAccount(a);lot.setRestaurant(o.getRestaurant());lot.setCustomer(o.getCustomer());lot.setOrder(o);
   lot.setEarnedPoints(netPoints);lot.setAvailablePoints(netPoints);
   if(p.getPointsExpiryDays()!=null) lot.setExpiresAt(LocalDateTime.now().plusDays(p.getPointsExpiryDays()));
   pointLots.save(lot);
  }
  saveTx(o.getRestaurant(),o.getCustomer(),o,null,"EARN",points,"Points earned for completed and paid order","earn:order:"+o.getId());
  if(debtPaid>0) saveTx(o.getRestaurant(),o.getCustomer(),o,null,"ADJUSTMENT",-debtPaid,"Points used to settle a refund reversal debt","refund-debt-settlement:"+o.getId());
 }
 private void expireAvailableLots(LoyaltyAccount account) {
  List<LoyaltyPointLot> expired=pointLots.lockExpiredLots(account.getId(),LocalDateTime.now());
  long total=0;
  for(LoyaltyPointLot lot:expired) {
   long points=Math.max(0,lot.getAvailablePoints());
   if(points==0) continue;
   lot.setAvailablePoints(0L);total+=points;
   saveTx(lot.getRestaurant(),lot.getCustomer(),lot.getOrder(),null,"EXPIRE",points,"Loyalty points expired","expire:lot:"+lot.getId());
  }
  if(total>0) {
   account.setAvailablePoints(Math.max(0,account.getAvailablePoints()-total));
   accounts.save(account);
  }
 }
 private void reservePointLots(LoyaltyAccount account,LoyaltyRedemption redemption,long required) {
  List<LoyaltyPointLot> lots=pointLots.lockUsableLots(account.getId(),LocalDateTime.now());
  long remaining=required;
  for(LoyaltyPointLot lot:lots) {
   if(remaining<=0) break;
   long take=Math.min(remaining,lot.getAvailablePoints());
   if(take<=0) continue;
   lot.setAvailablePoints(lot.getAvailablePoints()-take);
   LoyaltyPointAllocation allocation=new LoyaltyPointAllocation();
   allocation.setRedemption(redemption);allocation.setPointLot(lot);allocation.setPoints(take);allocation.setStatus("RESERVED");
   allocations.save(allocation);
   remaining-=take;
  }
  if(remaining>0) throw new IllegalArgumentException("Your available points have expired or changed. Refresh and try again.");
 }
 private void finalizePointAllocations(LoyaltyRedemption redemption) {
  for(LoyaltyPointAllocation allocation:allocations.findAllByRedemption_IdAndStatus(redemption.getId(),"RESERVED")) {
   allocation.setStatus("CONSUMED");allocations.save(allocation);
  }
 }
 private void releasePointAllocations(LoyaltyRedemption redemption,LoyaltyAccount account) {
  LocalDateTime now=LocalDateTime.now();
  for(LoyaltyPointAllocation allocation:allocations.findAllByRedemption_IdAndStatus(redemption.getId(),"RESERVED")) {
   LoyaltyPointLot lot=allocation.getPointLot();
   if(lot.getExpiresAt()==null||lot.getExpiresAt().isAfter(now)) {
    lot.setAvailablePoints(lot.getAvailablePoints()+allocation.getPoints());
    account.setAvailablePoints(account.getAvailablePoints()+allocation.getPoints());
   }
   allocation.setStatus("RELEASED");allocations.save(allocation);
  }
 }
 private void restoreRedeemedRedemptionsForRefund(RestaurantOrder order) {
  List<LoyaltyRedemption> redeemed=redemptions.findAllByOrder_IdAndStatus(order.getId(),"REDEEMED");
  for(LoyaltyRedemption redemption:redeemed) {
   String key="refund:redemption:"+redemption.getId();
   if(transactions.existsByIdempotencyKey(key)) continue;
   LoyaltyAccount account=accounts.lockAccount(order.getRestaurant().getId(),order.getCustomer().getId()).orElse(null);
   if(account==null) continue;
   long points=redemption.getPointsUsed();
   long debtPaid=Math.min(points,Math.max(0,account.getPointsDebt()));
   long restored=points-debtPaid;
   account.setPointsDebt(account.getPointsDebt()-debtPaid);
   account.setAvailablePoints(account.getAvailablePoints()+restored);
   account.setLifetimeRedeemedPoints(Math.max(0,account.getLifetimeRedeemedPoints()-points));
   accounts.save(account);
   if(restored>0) {
    LoyaltyProgram program=programs.findByRestaurant_Id(order.getRestaurant().getId()).orElse(null);
    LoyaltyPointLot lot=new LoyaltyPointLot();lot.setAccount(account);lot.setRestaurant(order.getRestaurant());lot.setCustomer(order.getCustomer());
    lot.setOrder(order);lot.setEarnedPoints(restored);lot.setAvailablePoints(restored);
    if(program!=null&&program.getPointsExpiryDays()!=null) lot.setExpiresAt(LocalDateTime.now().plusDays(program.getPointsExpiryDays()));
    pointLots.save(lot);
   }
   for(LoyaltyPointAllocation allocation:allocations.findAllByRedemption_IdAndStatus(redemption.getId(),"CONSUMED")) {
    allocation.setStatus("RELEASED");allocations.save(allocation);
   }
   LoyaltyReward reward=redemption.getReward();
   if(reward!=null){if(reward.getRemainingQuantity()!=null) reward.setRemainingQuantity(reward.getRemainingQuantity()+1);reward.setRedemptionCount(Math.max(0,reward.getRedemptionCount()-1));}
   redemption.setStatus("CANCELLED");redemptions.save(redemption);
   saveTx(order.getRestaurant(),order.getCustomer(),order,redemption,"RELEASE",points,
       debtPaid>0?"Refunded reward points partly applied to outstanding points debt":"Reward points restored after order refund",key);
  }
 }
 private void reverseEarnedPointsForRefund(RestaurantOrder order) {
  String reversalKey="refund:order:"+order.getId();
  if(transactions.existsByIdempotencyKey(reversalKey)) return;
  LoyaltyTransaction earned=transactions.findByOrder_IdAndTransactionType(order.getId(),"EARN").orElse(null);
  if(earned==null) return;
  LoyaltyAccount account=accounts.lockAccount(order.getRestaurant().getId(),order.getCustomer().getId()).orElse(null);
  if(account==null) return;
  long stillAvailable=0;
  for(LoyaltyPointLot lot:pointLots.lockLotsByOrder(order.getId())) {
   stillAvailable+=Math.max(0,lot.getAvailablePoints());
   lot.setAvailablePoints(0L);
  }
  account.setAvailablePoints(Math.max(0,account.getAvailablePoints()-stillAvailable));
  long debt=Math.max(0,earned.getPoints()-stillAvailable);
  account.setPointsDebt(account.getPointsDebt()+debt);
  accounts.save(account);
  saveTx(order.getRestaurant(),order.getCustomer(),order,null,"REFUND_REVERSAL",earned.getPoints(),
      debt>0?"Earned points reversed; spent points recorded as future points debt":"Earned points reversed after refund",reversalKey);
 }
 @Scheduled(fixedDelay = 60000)
 @Transactional
 public void expireAbandonedRedemptions() {
  LocalDateTime now=LocalDateTime.now();
  for(LoyaltyRedemption redemption:redemptions.lockExpiredPendingReservations(now)) {
   RestaurantOrder order=redemption.getOrder();
   if(order==null||order.getPaymentStatus()==PaymentStatus.PAID||order.getOrderStatus()==OrderStatus.SERVED) continue;
   LoyaltyAccount account=accounts.lockAccount(redemption.getRestaurant().getId(),redemption.getCustomer().getId()).orElse(null);
   if(account!=null) {
    releasePointAllocations(redemption,account);
    accounts.save(account);
   }
   LoyaltyReward reward=redemption.getReward();
   if(reward!=null){if(reward.getRemainingQuantity()!=null) reward.setRemainingQuantity(reward.getRemainingQuantity()+1);reward.setRedemptionCount(Math.max(0,reward.getRedemptionCount()-1));}
   redemption.setStatus("EXPIRED");redemptions.save(redemption);
   order.setRewardDiscountAmount(BigDecimal.ZERO);
   order.getItems().removeIf(item->item.getItemName()!=null&&item.getItemName().endsWith("(Reward)"));
   orderRepository.save(order);
   try { billService.refreshBill(order); } catch (ResourceNotFoundException ignored) { /* bill not created yet */ }
   saveTx(redemption.getRestaurant(),redemption.getCustomer(),order,redemption,"RELEASE",
       redemption.getPointsUsed(),"Checkout reservation expired and points were released","expire:redemption:"+redemption.getId());
  }
 }
 private void saveTx(Restaurant r,Customer c,RestaurantOrder o,LoyaltyRedemption red,String type,long pts,String desc,String key){if(transactions.existsByIdempotencyKey(key))return;LoyaltyTransaction t=new LoyaltyTransaction();t.setRestaurant(r);t.setCustomer(c);t.setOrder(o);t.setRedemption(red);t.setTransactionType(type);t.setPoints(pts);t.setDescription(desc);t.setIdempotencyKey(key);transactions.save(t);}
 private boolean available(LoyaltyReward r){LocalDateTime n=LocalDateTime.now();return r.isActive()&&!r.isDeleted()&&(r.getStartDate()==null||!r.getStartDate().isAfter(n))&&(r.getExpiryDate()==null||r.getExpiryDate().isAfter(n))&&(r.getRemainingQuantity()==null||r.getRemainingQuantity()>0)&&(r.getMaxRedemptions()==null||r.getRedemptionCount()<r.getMaxRedemptions());}
 private Map<String,Object> overviewRewardView(LoyaltyOverviewReward r,long redemptions){
  LocalDateTime now=LocalDateTime.now();Map<String,Object> m=new LinkedHashMap<>();
  m.put("id",r.id());m.put("rewardName",r.rewardName());m.put("description",r.description());m.put("imageUrl",null);
  m.put("rewardType",r.rewardType());m.put("pointsRequired",r.pointsRequired());m.put("menuItemId",r.menuItemId());
  m.put("menuItemName",r.menuItemName());m.put("discountPercentage",r.discountPercentage());
  m.put("discountAmount",r.discountAmount());m.put("maximumDiscount",r.maximumDiscount());
  m.put("minimumOrderAmount",r.minimumOrderAmount());m.put("totalQuantity",r.totalQuantity());
  m.put("remainingQuantity",r.remainingQuantity());m.put("maxRedemptions",r.maxRedemptions());
  m.put("redemptionCount",r.redemptionCount());m.put("startDate",r.startDate());m.put("expiryDate",r.expiryDate());
  m.put("active",r.active());m.put("available",r.active()&&(r.startDate()==null||!r.startDate().isAfter(now))
      &&(r.expiryDate()==null||r.expiryDate().isAfter(now))
      &&(r.remainingQuantity()==null||r.remainingQuantity()>0)
      &&(r.maxRedemptions()==null||r.redemptionCount()<r.maxRedemptions()));
  m.put("redemptions",redemptions);return m;
 }
 private Map<String,Object> settingsView(LoyaltyProgram p){Map<String,Object>m=new LinkedHashMap<>();m.put("enabled",p.isEnabled());m.put("pointsPer100",p.getPointsPer100());m.put("minimumOrderAmount",p.getMinimumOrderAmount());m.put("minimumRedemptionPoints",p.getMinimumRedemptionPoints());m.put("pointsExpiryDays",p.getPointsExpiryDays());m.put("conversionPoints",p.getConversionPoints());m.put("conversionInr",p.getConversionInr());return m;}
 private Map<String,Object> rewardView(LoyaltyReward r){
  long associationStartedAtNanos=DashboardRequestMeasurements.startStage();
  MenuItem menuItem=r.getMenuItem();
  Long menuItemId=menuItem==null?null:menuItem.getId();
  String menuItemName=menuItem==null?null:menuItem.getItemName();
  DashboardRequestMeasurements.recordStageDuration(
      Stage.OWNER_REWARD_ASSOCIATION_LOADING,associationStartedAtNanos);
  Map<String,Object>m=new LinkedHashMap<>();m.put("id",r.getId());m.put("rewardName",r.getRewardName());m.put("description",r.getDescription());m.put("imageUrl",r.getImageUrl());m.put("rewardType",r.getRewardType());m.put("pointsRequired",r.getPointsRequired());m.put("menuItemId",menuItemId);m.put("menuItemName",menuItemName);m.put("discountPercentage",r.getDiscountPercentage());m.put("discountAmount",r.getDiscountAmount());m.put("maximumDiscount",r.getMaximumDiscount());m.put("minimumOrderAmount",r.getMinimumOrderAmount());m.put("totalQuantity",r.getTotalQuantity());m.put("remainingQuantity",r.getRemainingQuantity());m.put("maxRedemptions",r.getMaxRedemptions());m.put("redemptionCount",r.getRedemptionCount());m.put("startDate",r.getStartDate());m.put("expiryDate",r.getExpiryDate());m.put("active",r.isActive());m.put("available",available(r));return m;
 }
 private Map<String,Object> txView(LoyaltyTransactionSummary t){
  Map<String,Object>m=new LinkedHashMap<>();m.put("id",t.id());m.put("type",t.type());
  long displayedPoints="RESERVATION".equals(t.type())||"REDEEM".equals(t.type())?-t.points():t.points();
  m.put("points",displayedPoints);m.put("description",t.description());
  m.put("orderId",t.orderId());m.put("createdAt",t.createdAt());return m;
 }
 private Map<String,Object> redemptionView(LoyaltyRedemptionSummary r){Map<String,Object>m=new LinkedHashMap<>();m.put("id",r.id());m.put("customerName",r.customerName());m.put("customerMobile",r.customerMobile());m.put("rewardName",r.rewardName()==null?"Point-to-INR redemption":r.rewardName());m.put("pointsUsed",r.pointsUsed());m.put("redemptionCode",r.redemptionCode());m.put("orderId",r.orderId());m.put("status",r.status());m.put("createdAt",r.createdAt());m.put("redeemedAt",r.redeemedAt());return m;}
 private record CustomerHistory(List<LoyaltyTransactionSummary> transactions,boolean hasMore) {}
 private <T> T measureRepositoryOperation(Stage stage,Supplier<T> operation) {
  return DashboardRequestMeasurements.measureRepositoryOperation(stage,operation);
 }
 private void recordRewardImagePayload(Iterable<LoyaltyReward> rewardEntities) {
  long imageCount=0,dataUrlCharacters=0,externalUrlCharacters=0;
  for(LoyaltyReward reward:rewardEntities) {
   String imageUrl=reward.getImageUrl();
   if(imageUrl==null||imageUrl.isBlank())continue;
   imageCount++;
   if(imageUrl.regionMatches(true,0,"data:image/",0,"data:image/".length()))
    dataUrlCharacters+=imageUrl.length();
   else externalUrlCharacters+=imageUrl.length();
  }
  DashboardRequestMeasurements.recordRewardImagePayload(
      imageCount,dataUrlCharacters,externalUrlCharacters);
 }
 private void audit(Restaurant restaurant,String action,String targetType,Long targetId,String details){
  String actor=SecurityContextHolder.getContext().getAuthentication()==null?"unknown":SecurityContextHolder.getContext().getAuthentication().getName();
  LoyaltyAuditLog log=new LoyaltyAuditLog();log.setRestaurant(restaurant);log.setActorIdentity(actor==null?"unknown":actor);log.setAction(action);log.setTargetType(targetType);log.setTargetId(targetId);log.setDetails(details);
  measureRepositoryOperation(Stage.OWNER_AUDIT_LOG_SAVE,()->auditLogs.save(log));
 }
 private int num(Object x,int d){return x instanceof Number n?n.intValue():d;} private BigDecimal decimal(Object x,BigDecimal d){try{return x==null?d:new BigDecimal(x.toString());}catch(Exception e){return d;}}
 private BigDecimal nullableDecimal(Object x){return x==null||x.toString().isBlank()?null:decimal(x,BigDecimal.ZERO);} private Integer nullableInt(Object x){return x==null||x.toString().isBlank()?null:num(x,0);}
 private Long nullableLong(Object x){return x==null?null:Long.valueOf(x.toString());} private String str(Object x){return x==null?"":x.toString().trim();}
 private LocalDateTime date(Object x){return x==null||x.toString().isBlank()?null:LocalDateTime.parse(x.toString());}
}
