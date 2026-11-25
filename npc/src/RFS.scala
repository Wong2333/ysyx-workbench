package npc
import chisel3._
import chisel3.util._

class WriteRegPortBundle extends Bundle{
    val wdata  = Output(UInt(32.W))
    val waddr  = Output(UInt(5.W))
    val wen    = Output(Bool())
}

class CSRWritePortBundle extends Bundle{
    val CSRWAddr = Output(UInt(12.W))
    val CSRWData = Output(UInt(32.W))
    val CSRWrite = Output(Bool())

}
class CSRReadPortBundle extends Bundle{
    val CSRRAddr = Output(UInt(12.W))
    val CSRRData = Input(UInt(32.W))

}
class ExceptionInfoBundle extends Bundle{
    val isTrap = Output(Bool())
    val PC = Output(UInt(32.W))
    val Exception_Code = Output(UInt(32.W))
}

class ReadRegPortBundle extends Bundle{

    val raddr1 = Output(UInt(5.W))
    val rdata1 = Input(UInt(32.W))
    val raddr2 = Output(UInt(5.W))
    val rdata2 = Input(UInt(32.W))
}

class ysyx_26030092_RegBlackBox extends ExtModule{
  val io = FlatIO(new Bundle {
    val clock = Input(Clock())
    val wdata  = Input(UInt(32.W))
    val waddr  = Input(UInt(5.W))
    val wen    = Input(Bool())
    val raddr1 = Input(UInt(5.W))
    val rdata1 = Output(UInt(32.W))
    val raddr2 = Input(UInt(5.W))
    val rdata2 = Output(UInt(32.W))
  })
  addResource("/RegBlackBox.v")
}

class ysyx_26030092_RFS extends Module {
    val RFS_ReadPort = IO(new Bundle {
        val RegReadPort = Flipped(new ReadRegPortBundle)
        val CSRReadPort = Flipped(new CSRReadPortBundle)
    })

    val WriteRegPort = IO(Input(new WriteRegPortBundle))
    val CSRWritePort = IO(Input(new CSRWritePortBundle))
    val ExceptionPort = IO(Input(new ExceptionInfoBundle))

    val mtvec_out = IO(Output(UInt(32.W)))
    val mtime_out = IO(Output(UInt(64.W)))

    val timer_intr = IO(Output(Bool()))
    val intr_taken = IO(Input(Bool()))
    val intr_pc    = IO(Input(UInt(32.W)))

    val trap = ExceptionPort.isTrap

    val Reg_u = Module(new ysyx_26030092_RegBlackBox())
    Reg_u.io.raddr1 := RFS_ReadPort.RegReadPort.raddr1
    Reg_u.io.raddr2 := RFS_ReadPort.RegReadPort.raddr2
    RFS_ReadPort.RegReadPort.rdata1 := Reg_u.io.rdata1
    RFS_ReadPort.RegReadPort.rdata2 := Reg_u.io.rdata2
    Reg_u.io.wdata := WriteRegPort.wdata
    Reg_u.io.waddr := WriteRegPort.waddr
    Reg_u.io.wen := WriteRegPort.wen && !trap
    Reg_u.io.clock := clock

    val mcycle = RegInit(0.U(64.W))
    mcycle := mcycle + 1.U
    val mepc = RegInit(0.U(32.W))
    val mcause = RegInit(0.U(32.W))
    val mtvec = RegInit(0.U(32.W))
    val mstatus = RegInit("h1800".U(32.W))

    // 倒计时定时器：mcycle 按 (prescale+1) 分频得到节拍，节拍到来时倒计一拍，
    // 减到 0 置 mtip；间隔 = (prescale+1) * ticks 个时钟周期
    val mtimer_prescale = RegInit(0.U(8.W))
    val mtimer_phase    = RegInit(0.U(8.W))
    val mtimer_ticks    = RegInit(0.U(14.W))
    val mtip            = RegInit(false.B)

    val mtimer_tick = mtimer_phase === mtimer_prescale
    mtimer_phase := Mux(mtimer_tick, 0.U, mtimer_phase + 1.U)

    when(mtimer_tick && mtimer_ticks =/= 0.U){
        mtimer_ticks := mtimer_ticks - 1.U
        when(mtimer_ticks === 1.U){ mtip := true.B }
    }
    when(intr_taken){ mtip := false.B }

    timer_intr := mtip

    mtvec_out := mtvec
    mtime_out := mcycle

    RFS_ReadPort.CSRReadPort.CSRRData := 0.U
    switch(RFS_ReadPort.CSRReadPort.CSRRAddr){
      is("hB00".U){// mcycle
        RFS_ReadPort.CSRReadPort.CSRRData := mcycle(31,0)
      }
      is("hB80".U){// mcycleh
        RFS_ReadPort.CSRReadPort.CSRRData := mcycle(63,32)
      }
      is("hF11".U){// mvendorid
        RFS_ReadPort.CSRReadPort.CSRRData := "h79737978".U
      }
      is("hF12".U){// marchid
        RFS_ReadPort.CSRReadPort.CSRRData := 26030092.U
      }
      is("h305".U){
        RFS_ReadPort.CSRReadPort.CSRRData := mtvec
      }
      is("h300".U){
        RFS_ReadPort.CSRReadPort.CSRRData := mstatus
      }
      is("h341".U){
        RFS_ReadPort.CSRReadPort.CSRRData := mepc
      }
      is("h342".U){
        RFS_ReadPort.CSRReadPort.CSRRData := mcause
      }
    }

    when(CSRWritePort.CSRWrite && !trap){
      switch(CSRWritePort.CSRWAddr){//mcycle
        is("hB00".U){
          mcycle := Cat(mcycle(63,32),CSRWritePort.CSRWData)
        }
        is("hB80".U){
          mcycle := Cat(CSRWritePort.CSRWData,mcycle(31,0))
        }
        is("h305".U){
          mtvec := CSRWritePort.CSRWData
        }
        is("h300".U){
          mstatus := CSRWritePort.CSRWData
        }
        is("h341".U){
          mepc := CSRWritePort.CSRWData
        }
        is("h7C0".U){// [21:16] 分频系数 prescale, [13:0] 倒计节拍数 ticks; 写 0 = 关闭
          mtimer_prescale := Cat(0.U(2.W), CSRWritePort.CSRWData(21,16))
          mtimer_ticks    := CSRWritePort.CSRWData(13,0)
          mtimer_phase    := 0.U
          mtip            := false.B
        }
        is("h342".U){
          mcause := CSRWritePort.CSRWData
        }
      }
    }

    when(intr_taken){
      mepc   := intr_pc
      mcause := "h80000007".U
    }

    when(ExceptionPort.isTrap){
      mepc := ExceptionPort.PC
      mcause := ExceptionPort.Exception_Code
    }
}
