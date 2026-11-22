package npc.icache

import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._

// 面积优化版 refill：不缓存整块（不再有 block_reg），按 AXI 每一拍直接给 icache 写一个字。
class ysyx_26030092_icacheRefillUnit_reg(block_size :Int) extends Module{
    val words = block_size / 4
    val word_bits = math.max(1, log2Ceil(math.max(2, words)))

    val flush = IO(Input(Bool()))
    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })

    val resp = IO(new Bundle {
        val data  = Output(UInt(32.W))
        val word  = Output(UInt(word_bits.W))
        val last  = Output(Bool())
        val addr  = Output(UInt(32.W))
        val valid = Output(Bool())
    })

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port.initMaster()

    val s_idle :: s_wait_mem_free :: s_wait_mem_data :: Nil = Enum(3)
    val state = RegInit(s_idle)
    PerfCounter("icache_refill_busy") := (state =/= s_idle)
    PerfCounter("icache_refill_count") := (state === s_idle) && req.valid

    val req_addr_reg = Reg(UInt(32.W))
    val beat_cnt = RegInit(0.U(word_bits.W))

    val block_bits = log2Ceil(block_size)
    val block_addr = Cat(req_addr_reg(31,block_bits),0.U(block_bits.W))
    val beats = words

    resp.data  := AXI4_port.rdata
    resp.word  := beat_cnt
    resp.last  := AXI4_port.rlast
    resp.addr  := req_addr_reg
    resp.valid := (state === s_wait_mem_data) && AXI4_port.rvalid

    req.ready := false.B
    AXI4_port.arvalid := false.B
    AXI4_port.araddr := 0.U
    AXI4_port.arlen := 0.U
    AXI4_port.arsize := 0.U
    AXI4_port.arburst := 0.U
    AXI4_port.rready := false.B

    switch(state){
        is(s_idle){
            req.ready := true.B
            when(req.valid & req.ready){
                req_addr_reg := req.addr
                beat_cnt := 0.U
                state := s_wait_mem_free
            }
        }
        is(s_wait_mem_free){
            AXI4_port.arvalid := true.B
            AXI4_port.araddr := block_addr
            AXI4_port.arlen := (beats - 1).U
            AXI4_port.arsize := 2.U
            AXI4_port.arburst := 1.U
            when(AXI4_port.arready){
                state := s_wait_mem_data
            }
        }
        is(s_wait_mem_data){
            when(AXI4_port.rvalid){
                AXI4_port.rready := true.B
                beat_cnt := beat_cnt + 1.U
                when(AXI4_port.rlast){
                    state := s_idle
                }
            }
        }
    }

    // 注意：flush 不能中途打断 AXI 读突发，否则从设备会一直等 rready 导致死锁。
    // 突发照常收完（数据写进 cache 无害），下一次请求在 idle 才被接受。
}
