
package net.guzhenren.tianyi.net;
public record EncounterActionPayload(Action action,String encounterId){public enum Action{INVESTIGATE,WAGER,FINISH}}
