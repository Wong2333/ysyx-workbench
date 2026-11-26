package backend
import chisel3._

class intrUnit extends Module{
    val timer_intr = IO(Input(Bool()))
    val timer_armed = IO(Input(Bool()))

    val intr = IO(Output(Bool()))
    val intr_taken = IO(Input(Bool()))

    val intr_flag = RegInit(false.B)
    intr := intr_flag

    when(timer_intr){
        intr_flag := true.B
    }
    when(intr_taken & intr_flag){
        intr_flag := false.B
    }


    val wfi = IO(Input(Bool()))
    val wfi_flag = RegInit(false.B)

    when(wfi) {wfi_flag := true.B}
    when(timer_intr) { wfi_flag := false.B}
    val sleep = IO(Output(Bool()))
    sleep := wfi_flag 
}