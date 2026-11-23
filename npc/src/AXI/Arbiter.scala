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

    // AR：用 readBusy/owner 把读事务串行化，保证同一时刻只有一个读事务在空中
    val readBusy = RegInit(false.B)
    val owner    = RegInit(0.U(sel_bits.W))
    val ar_req_bits = VecInit(master.map(_.arvalid)).asUInt
    val ar_sel  = Mux(readBusy, owner, PriorityEncoder(ar_req_bits))

    when(!readBusy && slave.arvalid && slave.arready) {
        readBusy := true.B
        owner    := ar_sel
    }
    when(readBusy && slave.rvalid && slave.rlast) {
        readBusy := false.B
    }

    slave.connectARFrom(master(ar_sel), !readBusy)
    slave.arid := ar_sel

    // R
    val rid_sel = Mux(readBusy, owner, slave.rid(sel_bits - 1, 0))
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

    slave.connectAWFrom(master(1), true.B)
    slave.connectWFrom(master(1), true.B)
    slave.connectBFrom(master(1), true.B)
}
