package net.minecraft.world.scores;
public class Scoreboard {
 private final java.util.Map<String,Objective> objectives=new java.util.HashMap<>();
 private final java.util.Map<String,java.util.Map<Objective,ScoreAccess>> players=new java.util.HashMap<>();
 public java.util.Collection<Objective> getObjectives(){return objectives.values();}
 public Objective getObjective(String n){return objectives.get(n);}
 public Objective addObjective(String n,Object criteria,net.minecraft.network.chat.Component label,Object render,boolean auto,Object format){var o=new Objective(n);objectives.put(n,o);return o;}
 public ScoreAccess getPlayerScoreInfo(ScoreHolder p,Objective o){return players.getOrDefault(p.name(),java.util.Map.of()).get(o);}
 public ScoreAccess getOrCreatePlayerScore(ScoreHolder p,Objective o){return players.computeIfAbsent(p.name(),k->new java.util.HashMap<>()).computeIfAbsent(o,k->new ScoreAccess());}
 public void resetAllPlayerScores(ScoreHolder p){players.remove(p.name());}
}