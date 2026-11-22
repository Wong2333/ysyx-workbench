package npc.icache

import chisel3._
import chisel3.util._

// 指令 Cache 的存储体（替代 icache_reg）—— 容量直接写死，不做参数化：
//   2 行 × 16 字节 = 32 字节，每行 4 个字（32 位）
//
// 数据按“字”存（8 × 32 位），配合 refill 逐拍写入：
//   AXI 每拍回来直接写一个字，因此不需要 128 位的整行暂存寄存器，
//   写口也只有 32 位宽。
//
// 端口与原来的 icache_reg 完全一致，仍由 icacheMainPipe_reg 调用。
class ysyx_26030092_icache extends Module {
    // ===== 容量（写死）=====
    val LINE_NUM  = 2                       // 行数
    val LINE_WORD = 4                       // 每行 4 个字 = 16 字节
    val INDEX_W   = 1                       // log2(LINE_NUM)
    val OFFSET_W  = 2                       // log2(LINE_WORD)
    val TAG_W     = 30 - INDEX_W - OFFSET_W // 27

    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
    })

    val resp = IO(new Bundle {
        val data = Output(UInt(32.W))
        val hit  = Output(Bool())
    })

    // refill：逐字写入，最后一拍写 tag/valid
    val refill = IO(new Bundle {
        val addr  = Input(UInt(32.W))
        val data  = Input(UInt(32.W))
        val word  = Input(UInt(OFFSET_W.W))
        val last  = Input(Bool())
        val valid = Input(Bool())
    })

    val flush_cache = IO(Input(Bool()))
    val flush       = IO(Input(Bool()))

    // ===== 存储 =====
    val data       = Reg(Vec(LINE_NUM * LINE_WORD, UInt(32.W)))
    val tag        = Reg(Vec(LINE_NUM, UInt(TAG_W.W)))
    val line_valid = RegInit(VecInit(Seq.fill(LINE_NUM)(false.B)))

    // ===== 读口（组合）=====
    val index  = req.addr(INDEX_W + OFFSET_W + 1, OFFSET_W + 2)
    val offset = req.addr(OFFSET_W + 1, 2)
    val cur_tag = req.addr(31, INDEX_W + OFFSET_W + 2)
    dontTouch(index)
    dontTouch(offset)
    dontTouch(cur_tag)

    val line = VecInit.tabulate(LINE_WORD)(i => data(index * LINE_WORD.U + i.U))
    resp.data := line(offset)
    resp.hit  := (cur_tag === tag(index)) && line_valid(index)

    // ===== 写口：逐字写入，最后一拍写 tag/valid =====
    val refill_index = refill.addr(INDEX_W + OFFSET_W + 1, OFFSET_W + 2)
    val refill_tag   = refill.addr(31, INDEX_W + OFFSET_W + 2)
    when(refill.valid) {
        data(refill_index * LINE_WORD.U + refill.word) := refill.data
        when(refill.last) {
            tag(refill_index)        := refill_tag
            line_valid(refill_index) := true.B
        }
    }

    when(flush_cache) {
        line_valid.foreach(_ := false.B)
    }
}
