package npc.icache

import chisel3._
import chisel3.util._

// 数据按“字”存储；refill 时 AXI 每拍直接写进对应字，不需要整行暂存寄存器。
// 因此要求：一行至少 8 字节（words = block_size/4 > 1），且 words / block_num 都是 2 的幂。
class ysyx_26030092_icache_reg(block_size :Int, block_num :Int) extends Module{
    val words = block_size / 4
    require(words > 1, s"icache block_size 至少 8 字节（当前 $block_size）")
    require(block_num > 1, s"icache block_num 至少 2（当前 $block_num）")
    require(isPow2(words), s"icache words 必须是 2 的幂（当前 $words）")
    require(isPow2(block_num), s"icache block_num 必须是 2 的幂（当前 $block_num）")

    val offset_bits = log2Ceil(words)
    val index_bits  = log2Ceil(block_num)
    val tag_bits    = 30 - offset_bits - index_bits

    val cache       = Reg(Vec(block_num * words, UInt(32.W)))
    val tag_array   = Reg(Vec(block_num, UInt(tag_bits.W)))
    val state_array = RegInit(VecInit(Seq.fill(block_num)(false.B)))

    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
    })

    val resp = IO(new Bundle {
        val data = Output(UInt(32.W))
        val hit = Output(Bool())
    })

    val refill = IO(new Bundle {
        val addr  = Input(UInt(32.W))
        val data  = Input(UInt(32.W))
        val word  = Input(UInt(offset_bits.W))
        val last  = Input(Bool())
        val valid = Input(Bool())
    })

    val flush_cache = IO(Input(Bool()))
    val flush = IO(Input(Bool()))

    val req_addr = req.addr
    val offset = req_addr(offset_bits + 1, 2)
    val index  = req_addr(offset_bits + index_bits + 1, offset_bits + 2)
    val tag    = req_addr(31, offset_bits + index_bits + 2)
    dontTouch(offset)
    dontTouch(index)
    dontTouch(tag)

    // 组合读：先从 cache 取出该行的 words 个字，再按 offset 选
    val block = VecInit.tabulate(words)(i => cache(index * words.U + i.U))
    resp.data := block(offset)
    resp.hit  := (tag === tag_array(index)) && state_array(index)

    // refill：逐字写入，最后一拍写 tag/valid
    val refill_index = refill.addr(offset_bits + index_bits + 1, offset_bits + 2)
    val refill_tag   = refill.addr(31, offset_bits + index_bits + 2)
    when(refill.valid){
        cache(refill_index * words.U + refill.word) := refill.data
        when(refill.last){
            tag_array(refill_index)   := refill_tag
            state_array(refill_index) := true.B
        }
    }

    when(flush_cache){
        state_array.foreach(_ := false.B)
    }
}
