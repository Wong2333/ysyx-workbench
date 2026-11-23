package npc.icache

import chisel3._
import chisel3.util._


class ysyx_26030092_icache extends Module {
    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
    })

    val resp = IO(new Bundle {
        val data = Output(UInt(32.W))
        val hit  = Output(Bool())
    })

    val refill = IO(new Bundle {
        val addr  = Input(UInt(32.W))
        val data  = Input(UInt(32.W))
        val last  = Input(Bool())
        val valid = Input(Bool())
    })

    val flush_cache = IO(Input(Bool()))
    val flush       = IO(Input(Bool()))

    val data       = Reg(Vec(8, UInt(32.W)))
    val tag        = Reg(Vec(2, UInt(27.W)))
    val word_valid = RegInit(VecInit(Seq.fill(8)(false.B)))

    val offset  = req.addr(3, 2)
    val index   = req.addr(4)
    val cur_tag = req.addr(31, 5)
    dontTouch(index)
    dontTouch(offset)
    dontTouch(cur_tag)

    val req_word = Cat(index, offset)

    resp.data := data(req_word)
    resp.hit  := (cur_tag === tag(index)) && word_valid(req_word)

    val refill_offset = refill.addr(3, 2)
    val refill_index  = refill.addr(4)
    val refill_word   = Cat(refill_index, refill_offset)
    val refill_tag    = refill.addr(31, 5)
    when(refill.valid) {
        data(refill_word)       := refill.data
        word_valid(refill_word) := true.B
        when(refill.last) {
            tag(refill_index) := refill_tag
        }
    }

    when(flush_cache) {
        word_valid.foreach(_ := false.B)
    }
}
