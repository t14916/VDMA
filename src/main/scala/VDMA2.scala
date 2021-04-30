import chisel3._
import chisel3.util._
import freechips.rocketchip.subsystem.{BaseSubsystem, CacheBlockBytes}
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, IdRange}
import testchipip.TLHelper

//Is this right??
case class FBParams(
  compatible: String = "simple-framebuffer",
  address: BigInt = 0x2000,
  size: BigInt = 600*800*2,
  width: Int = 600,
  height: Int = 800,
  stride: Int = 1200,
  format: String = "r5g6b5",
  useAXI4: Boolean = True,
  useBlackBox: Boolean = True
)

//Framebuffer key
case object FBKey extends Field[Option[FBParams]](None)

class FBModule(implicit p: Parameters) extends LazyModule {
  val node = TLHelper.makeClientNode(
    name = "simple-framebuffer", sourceId = IdRange(0, 1))

  lazy val module = new FBModuleImp(this)
}

class FBModuleImp(outer: InitZero) extends LazyModuleImp(outer) {
  val config = p(InitZeroKey).get

  val (mem, edge) = outer.node.out(0)
  val addrBits = edge.bundle.addressBits
  val blockBytes = p(CacheBlockBytes)

  //require(config.size % blockBytes == 0) Not necessarily a requirement!!
  
  val s_init :: s_read :: s_resp :: s_idle :: Nil = Enum(4)
  val state = RegInit(s_init)

  val addr = Reg(UInt(addrBits.W))
  val data_read = Reg(UInt(log2Ceil(blockBytes).W))
  val bytes_left = Reg(UInt(log2Ceil(config.size+1).W))
  val cycle_counter = Reg(UInt(log2Ceil(config.pollrate).W))
  val bytes_to_read = Reg(UInt(log2Ceil(blockBytes).W))
  val mask = ((1 << log2Ceil(blockBytes)) - 1).U //bitmask for bottom log2Ceil(blockBytes) registers
  mem.a.valid := state === s_write
  data_read := edge.get(
    fromSource = 0.U,
    toAddress = addr,
    lgSize = log2Ceil(blockBytes).U)._2
  mem.d.ready := state === s_resp
  
  when (state === s_init) {
    addr := config.base.U
    bytesLeft := config.size.U
    data := 0.U
    state := s_write
    cycle_counter := 0
    bytes_to_read := blockBytes.U
  }.elsewhen (cycle_counter == config.pollrate) {
    cycle_counter := 0.U
    when (state === s_idle) {
      //This should be true at this point
      //Basically, idle after done reading the buffer, and then get back to poll state
      state := s_read
    }
  }.otherwise {
    cycle_counter := cycle_counter + 1.U
  }

  when (edge.done(mem.a)) {
    addr := addr + bytes_to_read.U
    bytes_left := bytes_left - blockBytes.U
    bytes_to_read := (bytes_left - bytes_to_read.U) & mask
    //Offload to some sort of internal buffer that has sends the data somehow to the host
    //Probably where I need to handle all the stuff that involves the bridge
    state := s_resp
  }

  when (mem.d.fire()) {
    state := Mux(bytes_left === 0.U, s_idle, s_read) //If no bytes are left, then
  }
}

trait CanHavePeripheryFB { this: BaseSubsystem =>
  implicit val p: Parameters

  p(InitZeroKey) .map { k =>
    val initZero = LazyModule(new FBModule()(p))
    fbus.fromPort(Some("FB"))() := FBModule.node
  }
}


// DOC include start: WithInitZero
class WithFB(base: BigInt, size: BigInt) extends Config((site, here, up) => {
  case FBKey => Some(FBConfig(base, size))
})
// DOC include end: WithInitZero
