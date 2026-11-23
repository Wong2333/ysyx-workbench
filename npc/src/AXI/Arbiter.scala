package npc.AXI
import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._

class ysyx_26030092_AXI4Arbiter(n_in : Int) extends Module{
    val master = IO(Vec(n_in, Flipped(new AXI4Bundle)))
    val slave = IO(new AXI4Bundle)

    for(i <- 0 until n_in){
        master(i).initSlave()
    }
    slave.initMaster()

    val sel_bits = log2Ceil(n_in)

    // AR
    val arLock  = RegInit(false.B)
    val arLockSel = RegInit(0.U(sel_bits.W))
    val ar_req_bits = VecInit(master.map(_.arvalid)).asUInt
    val ar_sel  = Mux(arLock, arLockSel, PriorityEncoder(ar_req_bits))
    when(slave.arvalid && !slave.arready && !arLock) { // 锁存ar_sel
        arLock    := true.B
        arLockSel := ar_sel
    }
    when(slave.arvalid && slave.arready) {
        arLock := false.B
    }
    when(arLock && !master(arLockSel).arvalid) {
        arLock := false.B
    }
    slave.connectARFrom(master(ar_sel), true.B)
    slave.arid := ar_sel

    // R
    val rid_sel = slave.rid(sel_bits - 1, 0)
    for(i <- 0 until n_in){
        master(i).rvalid := slave.rvalid && (rid_sel === i.U)
        master(i).rid    := slave.rid
        master(i).rdata  := slave.rdata
        master(i).rresp  := slave.rresp
        master(i).rlast  := slave.rlast
    }
    when(slave.rvalid){
        slave.rready := master(rid_sel).rready
    }

    
    when(slave.arvalid && slave.arready){
        printf("[CPU-AR] id=%d addr=%x\n", slave.arid, slave.araddr)
    }
    when(slave.rvalid && slave.rready){
        printf("[CPU-R ] id=%d data=%x last=%d\n", slave.rid, slave.rdata, slave.rlast)
    }
    slave.connectAWFrom(master(1), true.B)
    slave.connectWFrom(master(1), true.B)
    slave.connectBFrom(master(1), true.B)
}
