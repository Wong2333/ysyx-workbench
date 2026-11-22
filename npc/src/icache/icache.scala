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

    val line0 = Reg(Vec(4, UInt(32.W)))
    val line1 = Reg(Vec(4, UInt(32.W)))
    val tag        = Reg(Vec(2, UInt(27.W)))
    val line_valid = RegInit(VecInit(Seq.fill(2)(false.B)))

    val offset  = req.addr(3, 2)
    val index   = req.addr(4)
    val cur_tag = req.addr(31, 5)
    dontTouch(index)
    dontTouch(offset)
    dontTouch(cur_tag)

    resp.data := Mux(index === 0.U, line0(offset), line1(offset))
    resp.hit  := (cur_tag === tag(index)) && line_valid(index)

    val refill_offset = refill.addr(3, 2)
    val refill_index  = refill.addr(4)
    val refill_tag    = refill.addr(31, 5)
    when(refill.valid) {
        when(refill_index === 0.U) { line0(refill_offset) := refill.data }
        .otherwise                 { line1(refill_offset) := refill.data }
        when(refill.last) {
            tag(refill_index)        := refill_tag
            line_valid(refill_index) := true.B
        }
    }

    when(flush_cache) {
        line_valid.foreach(_ := false.B)
    }
}
