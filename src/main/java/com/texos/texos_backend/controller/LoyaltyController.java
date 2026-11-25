package com.texos.texos_backend.controller;

import com.texos.texos_backend.service.LoyaltyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class LoyaltyController {
 private final LoyaltyService loyalty;
 @GetMapping("/api/loyalty/status")
 public Map<String,Object> customerStatus(@RequestParam String qrToken,@RequestParam(required=false) String from,
      @RequestParam(required=false) String to,@RequestParam(required=false) Integer historyPage,
      @RequestParam(required=false) Integer historySize,HttpServletRequest request){
  return loyalty.customerStatus(qrToken,request,from,to,historyPage,historySize);
 }
 @GetMapping("/api/loyalty/balance")
 public Map<String,Object> balance(@RequestParam String qrToken,HttpServletRequest request){return loyalty.customerBalance(qrToken,request);}
 @GetMapping("/api/restaurants/{restaurantId}/loyalty/overview")
 public Map<String,Object> overview(@PathVariable Long restaurantId){return loyalty.overview(restaurantId);}
 @GetMapping("/api/restaurants/{restaurantId}/loyalty/settings")
 public Map<String,Object> settings(@PathVariable Long restaurantId){return loyalty.ownerSettings(restaurantId);}
 @PutMapping("/api/restaurants/{restaurantId}/loyalty/settings")
 public Map<String,Object> updateSettings(@PathVariable Long restaurantId,@RequestBody Map<String,Object> body){return loyalty.updateSettings(restaurantId,body);}
 @GetMapping("/api/restaurants/{restaurantId}/loyalty/rewards")
 public List<Map<String,Object>> rewards(@PathVariable Long restaurantId){return loyalty.ownerRewards(restaurantId);}
 @PostMapping("/api/restaurants/{restaurantId}/loyalty/rewards")
 public Map<String,Object> createReward(@PathVariable Long restaurantId,@RequestBody Map<String,Object> body){return loyalty.saveReward(restaurantId,null,body);}
 @PutMapping("/api/restaurants/{restaurantId}/loyalty/rewards/{rewardId}")
 public Map<String,Object> updateReward(@PathVariable Long restaurantId,@PathVariable Long rewardId,@RequestBody Map<String,Object> body){return loyalty.saveReward(restaurantId,rewardId,body);}
 @PatchMapping("/api/restaurants/{restaurantId}/loyalty/rewards/{rewardId}/status")
 public Map<String,Object> status(@PathVariable Long restaurantId,@PathVariable Long rewardId,@RequestBody Map<String,Object> body){return loyalty.setRewardStatus(restaurantId,rewardId,Boolean.TRUE.equals(body.get("active")));}
 @DeleteMapping("/api/restaurants/{restaurantId}/loyalty/rewards/{rewardId}")
 public Map<String,Object> delete(@PathVariable Long restaurantId,@PathVariable Long rewardId){loyalty.deleteReward(restaurantId,rewardId);return Map.of("message","Reward deactivated");}
 @GetMapping("/api/restaurants/{restaurantId}/loyalty/redemptions")
 public List<Map<String,Object>> redemptions(@PathVariable Long restaurantId){return loyalty.ownerRedemptions(restaurantId);}
 @GetMapping("/api/restaurants/{restaurantId}/loyalty/audit-logs")
 public List<Map<String,Object>> auditLogs(@PathVariable Long restaurantId){return loyalty.ownerAuditLogs(restaurantId);}
}
