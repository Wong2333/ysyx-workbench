#ifndef NPC_H__
#define NPC_H__

#define SERIAL_PORT      (0x10000000)
#define RTC_ADDR         (0x10000000 + 48)

/* 机器模式计时器中断（自定义 CSR）。
 * 硬件按固定分频产生节拍（NPC 上为每 512 个 mcycle 一拍），
 * 写入 ticks 表示再过 ticks 个节拍触发一次 mtip 中断，写 0 关闭。
 * 一次性触发：处理程序里必须重新写入。 */
#define MTIMER_CSR       (0x7c0)
static inline void npc_mtimer_start(uint32_t ticks) {
  asm volatile("csrw 0x7c0, %0" : : "r"(ticks));
}
static inline void npc_mtimer_stop(void) {
  asm volatile("csrw 0x7c0, zero");
}
#endif