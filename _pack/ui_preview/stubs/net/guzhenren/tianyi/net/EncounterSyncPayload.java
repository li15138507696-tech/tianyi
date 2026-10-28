
package net.guzhenren.tianyi.net;
public record EncounterSyncPayload(String id,String phase,String dimension,long position,int secondsLeft,
 String enemyId,String enemyName,int baseReward,int stake,int bonus,String message){}
