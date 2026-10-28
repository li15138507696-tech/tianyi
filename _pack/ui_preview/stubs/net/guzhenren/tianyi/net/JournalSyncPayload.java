
package net.guzhenren.tianyi.net; import java.util.*;
public record JournalSyncPayload(boolean titleUnlocked,long now,List<Row> rows,String title){
 public record Row(String id,int kind,int source,String title,String detail,String dim,long pos,long observedAt,long verifiedAt,boolean favorite,boolean doubtful,String caseId,String caseTitle,String caseMissing){}
}