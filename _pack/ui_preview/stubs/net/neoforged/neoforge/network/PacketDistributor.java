
package net.neoforged.neoforge.network; import java.util.*;
public class PacketDistributor {public static final List<Object> sent=new ArrayList<>();public static void sendToServer(Object p){sent.add(p);}}
