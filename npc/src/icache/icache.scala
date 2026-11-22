package npc.icache

import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._
import npc.instEntry
import npc.ysyxPrefix

// 直接映射指令 Cache —— 容量直接写死，不做参数化：
//   2 行 × 16 字节 = 32 字节，每行 4 个字（32 位）
//
// 面积上的几个取舍：
//   1. 数据按“字”存（8 × 32 位），AXI 每拍回来直接写一个字，没有整行暂存寄存器；
//   2. 只有 1 个地址寄存器，没有请求 FIFO；
//   3. AXI 读突发一旦发出就一定收完，flush 不打断（否则从设备等不到 rready 会死锁）。
class ysyx_26030092_icache extends Module {
    // ===== 容量（写死）=====
    val LINE_NUM  = 2                       // 行数
    val LINE_WORD = 4                       // 每行 4 个字 = 16 字节
    val INDEX_W   = 1                       // log2(LINE_NUM)
    val OFFSET_W  = 2                       // log2(LINE_WORD)
    val TAG_W     = 30 - INDEX_W - OFFSET_W // 27

    val flush        = IO(Input(Bool()))    // 分支/异常重定向：丢弃当前请求
    val icache_flush = IO(Input(Bool()))    // fence.i：清空 cache

    val req = IO(new Bundle {
        val addr  = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })

    val resp = IO(Decoupled(new instEntry))

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port.initMaster()

    // ===== 存储 =====
    val data       = Reg(Vec(LINE_NUM * LINE_WORD, UInt(32.W)))
    val tag        = Reg(Vec(LINE_NUM, UInt(TAG_W.W)))
    val line_valid = RegInit(VecInit(Seq.fill(LINE_NUM)(false.B)))

    // ===== 状态 =====
    val pending  = RegInit(false.B)                       // addr_reg 里是否有待响应的地址
    val addr_reg = Reg(UInt(32.W))
    val cnt      = RegInit(0.U(OFFSET_W.W))               // 当前是行内第几拍

    val s_normal :: s_ar :: s_rdata :: Nil = Enum(3)
    val state = RegInit(s_normal)
    val discard = RegInit(false.B)                        // flush 后还在收的突发，收完直接丢弃

    // ===== 组合查找 =====
    val index  = addr_reg(INDEX_W + OFFSET_W + 1, OFFSET_W + 2)
    val offset = addr_reg(OFFSET_W + 1, 2)
    val cur_tag = addr_reg(31, INDEX_W + OFFSET_W + 2)

    val hit  = pending && line_valid(index) && (tag(index) === cur_tag) && (state === s_normal)
    val word = data(index * LINE_WORD.U + offset)

    val refill_index = addr_reg(INDEX_W + OFFSET_W + 1, OFFSET_W + 2)
    val refill_tag   = addr_reg(31, INDEX_W + OFFSET_W + 2)

    PerfCounter("icache_refill_busy")  := (state =/= s_normal)
    PerfCounter("icache_refill_count") := (state === s_normal) && pending && !hit

    // ===== 默认值 =====
    req.ready := false.B
    resp.valid := hit
    resp.bits.pc        := Cat(addr_reg(31, 2), 0.U(2.W))
    resp.bits.inst      := word
    resp.bits.excp_code := 0.U

    val excp_trap = RegInit(false.B)
    when(AXI4_port.rvalid && (AXI4_port.rresp =/= 0.U)) { excp_trap := true.B }
    when(resp.valid && resp.ready)                      { excp_trap := false.B }
    resp.bits.is_excp := excp_trap

    AXI4_port.arvalid := false.B
    AXI4_port.araddr  := 0.U
    AXI4_port.arlen   := 0.U
    AXI4_port.arsize  := 0.U
    AXI4_port.arburst := 0.U
    AXI4_port.rready  := false.B

    // ===== 主流程 =====
    switch(state) {
        is(s_normal) {
            // 命中时当拍就能被取走，同时可以接收下一条取指地址（1 拍 1 条）
            req.ready := !pending || (hit && resp.ready)

            when(hit && resp.ready) { pending := false.B }
            when(req.valid && req.ready) {
                addr_reg := req.addr
                pending  := true.B
            }
            when(pending && !hit) {          // miss：发起 refill
                state := s_ar
            }
        }
        is(s_ar) {
            AXI4_port.arvalid := true.B
            AXI4_port.araddr  := Cat(addr_reg(31, 4), 0.U(4.W))   // 16 字节对齐
            AXI4_port.arlen   := (LINE_WORD - 1).U
            AXI4_port.arsize  := 2.U
            AXI4_port.arburst := 1.U
            when(AXI4_port.arready) {
                cnt   := 0.U
                state := s_rdata
            }
        }
        is(s_rdata) {
            AXI4_port.rready := true.B
            when(AXI4_port.rvalid) {
                // 逐字写入，最后一拍写 tag/valid
                data(refill_index * LINE_WORD.U + cnt) := AXI4_port.rdata
                when(AXI4_port.rlast) {
                    tag(refill_index)        := refill_tag
                    line_valid(refill_index) := true.B
                    state   := s_normal
                    discard := false.B
                    when(discard) { pending := false.B }
                }.otherwise {
                    cnt := cnt + 1.U
                }
            }
        }
    }

    // ===== flush =====
    when(icache_flush) {
        line_valid.foreach(_ := false.B)
    }

    when(flush) {
        resp.valid := false.B
        when(state === s_normal) {
            pending := false.B
        }.otherwise {
            // 突发已经在飞，收完再丢；不要打断 AXI 读，否则死锁
            discard := true.B
        }
    }
}
