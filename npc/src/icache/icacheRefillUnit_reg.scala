package npc.icache

import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._


class ysyx_26030092_icacheRefillUnit_reg extends Module{
    val words     = 4
    val word_bits = 2

    val flush = IO(Input(Bool()))
    val req = IO(new Bundle {
        val addr  = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })

    val resp = IO(new Bundle {
        val data  = Output(UInt(32.W))
        val last  = Output(Bool())
        val addr  = Output(UInt(32.W))
        val valid = Output(Bool())
    })

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port.initMaster()

    val s_idle :: s_ar :: s_data :: Nil = Enum(3)
    val state = RegInit(s_idle)
    PerfCounter("icache_refill_busy")  := (state =/= s_idle)
    PerfCounter("icache_refill_count") := (state === s_idle) && req.valid

    val line_addr = Reg(UInt(32.W))
    val word_cnt  = RegInit(0.U(word_bits.W))
    val burst     = RegInit(false.B)

    val done = word_cnt === (words - 1).U

    resp.data  := AXI4_port.rdata
    resp.addr  := line_addr + (word_cnt << 2)
    resp.valid := (state === s_data) && AXI4_port.rvalid
    resp.last  := (state === s_data) && AXI4_port.rvalid && Mux(burst, AXI4_port.rlast, done)

    req.ready         := false.B
    AXI4_port.arvalid := false.B
    AXI4_port.araddr  := 0.U
    AXI4_port.arlen   := 0.U
    AXI4_port.arsize  := 0.U
    AXI4_port.arburst := 0.U
    AXI4_port.rready  := false.B

    switch(state){
        is(s_idle){
            req.ready := true.B
            when(req.valid){
                line_addr := Cat(req.addr(31,4), 0.U(4.W))
                burst     := req.addr(31,28) === "h8".U
                word_cnt  := 0.U
                state     := s_ar
            }
        }
        is(s_ar){
            AXI4_port.arvalid := true.B
            AXI4_port.araddr  := Mux(burst, line_addr, line_addr + (word_cnt << 2))
            AXI4_port.arlen   := Mux(burst, (words - 1).U, 0.U)
            AXI4_port.arsize  := 2.U
            AXI4_port.arburst := 1.U
            when(AXI4_port.arready){
                state := s_data
            }
        }
        is(s_data){
            AXI4_port.rready := true.B
            when(AXI4_port.rvalid){
                when(burst){
                    when(AXI4_port.rlast){ state := s_idle }
                    .otherwise{ word_cnt := word_cnt + 1.U }
                }.otherwise{
                    when(done){ state := s_idle }
                    .otherwise{ word_cnt := word_cnt + 1.U; state := s_ar }
                }
            }
        }
    }
}
