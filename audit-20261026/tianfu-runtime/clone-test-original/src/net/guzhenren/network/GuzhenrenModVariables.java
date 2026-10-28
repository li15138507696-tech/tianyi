package net.guzhenren.network;
public class GuzhenrenModVariables {
 public record Key<T>(){} public static Key<PlayerVariables> PLAYER_VARIABLES=new Key<>();
 public static class PlayerVariables {
  public double tizhi,zhenyuan=10,zuida_zhenyuan=100,niantou=10,niantou_rongliang=200,cd;
  public double dahen_qidao,dahen_zhoudao,daohen_jindao,daohen_lvdao,daohen_tudao,daohen_xuedao,daohen_yudao;
  public double shouyuan=1000,kongqiao=7,qiyun=10,qiyun_shangxian=100,yuanshishuliang;
  public int dirty; public void markSyncDirty(){dirty++;}
 }
}