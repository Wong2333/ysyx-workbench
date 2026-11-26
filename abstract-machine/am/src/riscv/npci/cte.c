#include <am.h>
#include <riscv/riscv.h>
#include <klib.h>

static Context* (*user_handler)(Event, Context*) = NULL;

Context* __am_irq_handle(Context *c) {
  if (user_handler) {
    Event ev = {0};
    switch (c->mcause) {
      case 11:
        if(c->GPR1 == -1){
          ev.event = EVENT_YIELD;
          c->mepc += 4;
        }
      break;
      case 0x80000007u:  // 计时器中断：mepc 指向被打断的指令，不需要 +4
        ev.event = EVENT_IRQ_TIMER;
      break;
      default: ev.event = EVENT_ERROR; break;
    }

    c = user_handler(ev, c);
    /* RTL 每次陷阱会清 0x7C1，这里在返回前重新打开中断 */
    asm volatile("csrw 0x7c1, %0" : : "r"(1));
    assert(c != NULL);
  }

  return c;
}

extern void __am_asm_trap(void);

bool cte_init(Context*(*handler)(Event, Context*)) {
  // initialize exception entry
  asm volatile("csrw mtvec, %0" : : "r"(__am_asm_trap));

  // register event handler
  user_handler = handler;

  return true;
}

Context *kcontext(Area kstack, void (*entry)(void *), void *arg) {
  Context *cp = (Context *)((uintptr_t)kstack.end - sizeof(Context));
  cp->mepc = (uintptr_t)entry;
  cp->mstatus = 0x1800;
  cp->gpr[10] = (uintptr_t)arg;
  *((Context**)(kstack.start)) = cp;
  return cp;
}

void yield() {
#ifdef __riscv_e
  asm volatile("li a5, -1; ecall");
#else
  asm volatile("li a7, -1; ecall");
#endif
}

bool ienabled() {
  return false;
}

void iset(bool enable) {
}
