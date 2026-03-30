package com.deathhead.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 플레이어 사망 시 DeathHead 처리 전에 호출됩니다.
 * 전체 취소하거나 개별 기능을 선택적으로 비활성화할 수 있습니다.
 *
 * <pre>
 * // 사용 예시
 * {@literal @}EventHandler
 * public void onDeathHead(DeathHeadDeathEvent e) {
 *     // 전체 취소
 *     e.setCancelled(true);
 *
 *     // 연출만 취소
 *     e.setAnimationCancelled(true);
 *
 *     // 아이템 봉인만 취소
 *     e.setPenaltyCancelled(true);
 *
 *     // 머리 드롭만 취소
 *     e.setHeadDropCancelled(true);
 *
 *     // 봉인될 아이템 수정
 *     e.getSealedItems().clear();
 * }
 * </pre>
 */
public class DeathHeadDeathEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private boolean cancelled = false;
    private boolean animationCancelled = false;
    private boolean penaltyCancelled = false;
    private boolean headDropCancelled = false;
    private final Location deathLocation;
    private final List<ItemStack> sealedItems;

    public DeathHeadDeathEvent(@NotNull Player player, @NotNull Location deathLocation,
                               @NotNull List<ItemStack> sealedItems) {
        super(player);
        this.deathLocation = deathLocation;
        this.sealedItems = sealedItems;
    }

    /** 전체 DeathHead 처리 취소 (바닐라 사망만 발생) */
    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    /** 항공샷 카메라 연출 취소 */
    public boolean isAnimationCancelled() { return animationCancelled; }
    public void setAnimationCancelled(boolean cancel) { this.animationCancelled = cancel; }

    /** 아이템 봉인 취소 (인벤토리에서 아이템을 제거하지 않음) */
    public boolean isPenaltyCancelled() { return penaltyCancelled; }
    public void setPenaltyCancelled(boolean cancel) { this.penaltyCancelled = cancel; }

    /** 머리 드롭 취소 */
    public boolean isHeadDropCancelled() { return headDropCancelled; }
    public void setHeadDropCancelled(boolean cancel) { this.headDropCancelled = cancel; }

    /** 사망 위치 */
    @NotNull
    public Location getDeathLocation() { return deathLocation.clone(); }

    /** 봉인될 아이템 목록 (수정 가능 — 아이템 추가/제거/교체 가능) */
    @NotNull
    public List<ItemStack> getSealedItems() { return sealedItems; }

    @NotNull
    @Override
    public HandlerList getHandlers() { return HANDLER_LIST; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLER_LIST; }
}
