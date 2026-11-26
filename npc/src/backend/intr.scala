package backend
import chisel3._

class intrUnit extends Module{
    val timer_intr = IO(Input(Bool()))// from and to RFS
    val timer_armed = IO(Input(Bool()))
    val timer_intr_taken = IO(Output(Bool()))
    val mepc = IO(Output(UInt(32.W)))

    val intr = IO(Output(Bool())) // from and to EXU
    val intr_taken = IO(Input(Bool()))
    val excp_taken = IO(Input(Bool()))
    val intr_pc = IO(Input(UInt(32.W)))
    mepc := intr_pc

    val intr_flag = RegInit(false.B)
    intr := intr_flag

    when(timer_intr){
        intr_flag := true.B
    }
    when(intr_taken & intr_flag){
        intr_flag := false.B
        timer_intr_taken := true.B
    }

    val wfi = IO(Input(Bool()))
    val wfi_flag = RegInit(false.B)

    when(wfi & timer_armed) {wfi_flag := true.B}
    when(timer_intr) { wfi_flag := false.B}
    val sleep = IO(Output(Bool()))
    sleep := wfi_flag 

    val intr_close = IO(Output(Bool()))
    intr_close := intr_taken || excp_taken
}