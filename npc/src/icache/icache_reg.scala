package npc.icache

import chisel3._
import chisel3.util._

// 面积优化版 data cache 存储：
// 数据按“字”存储（block_num * words 个 32 位寄存器），refill 时逐字写入，
// 不再需要 128 位的 block 寄存器 / 128 位写 mux（省约 100+ 个触发器和大 mux）。
class ysyx_26030092_icache_reg(block_size :Int, block_num :Int) extends Module{
    val words = block_size / 4
    val cache = Reg(Vec(block_num * words, UInt(32.W)))
    val offset_bits = log2Ceil(block_size/4)
    val index_bits = log2Ceil(block_num)
    val tag_bits = 30 - offset_bits - index_bits
    val tag_array = Reg(Vec(block_num, UInt(tag_bits.W)))
    val state_array = RegInit(VecInit(Seq.fill(block_num)(false.B)))

    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
    })

    val resp = IO(new Bundle {
        val data = Output(UInt(32.W))
        val hit = Output(Bool())
    })

    val refill = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val data = Input(UInt(32.W))
        val word = Input(UInt(math.max(1, log2Ceil(math.max(2, words))).W))
        val last = Input(Bool())
        val valid = Input(Bool())
    })

    val flush_cache = IO(Input(Bool()))
    val flush = IO(Input(Bool()))

    val req_addr = req.addr
    val offset = (if (words > 1) req_addr(offset_bits + 1, 2) else 0.U(1.W))
    val index = (if (block_num > 1) req_addr(offset_bits + index_bits + 1, offset_bits + 2) else 0.U(1.W))
    dontTouch(offset)
    dontTouch(index)

    val block = VecInit.tabulate(words)(i => cache(index * words.U + i.U))
    val taget_tag = tag_array(index)
    val tag = req_addr(31, offset_bits + index_bits + 2)
    dontTouch(tag)

    resp.data := (if (words > 1) block(offset) else block(0))
    resp.hit := (tag === taget_tag) && state_array(index)

    // refill：逐字写入，最后一拍写 tag/valid
    val refill_index = (if (block_num > 1) refill.addr(offset_bits + index_bits + 1, offset_bits + 2) else 0.U(1.W))
    val refill_tag = refill.addr(31, offset_bits + index_bits + 2)
    when(refill.valid){
        cache(refill_index * words.U + refill.word) := refill.data
        when(refill.last){
            tag_array(refill_index) := refill_tag
            state_array(refill_index) := true.B
        }
    }

    when(flush_cache){
        state_array.foreach(_ := false.B)
    }
}
