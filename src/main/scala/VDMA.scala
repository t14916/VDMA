package chipyard.example

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

//Framebuffer parameters
//Parameters here turn into framebuffer?
//https://www.kernel.org/doc/Documentation/devicetree/bindings/display/simple-framebuffer.txt
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

//FBIO object
class FBIO(val width: Int, val height: Int, val pollrate: Int) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val out_ready = Input(Bool())
  val out_valid = Output(Bool())
  //val image = Output -> what is the best way to expose the image to other hardware???
}

//?? I'm not sure what TopIO should have in it
trait FBTopIO extends Bundle {
  val fb_busy = Output(Bool())
}

//What does this class do?
trait HasFBIO extends BaseModule {
  val width: int
  val height: int
  val io = IO(new FBIO(width, height))
}

//do I need this MMIOBlackbox class?? What does this do?

//Framebuffer MMIO, of size width x height
class FBMMIO(val width: Int, val height: Int, val pollrate: Int) extends Module
  with HasFBIO
{
}

class FBModule extends HasRegMap {
  val io: FBTopIO
  implicit val p: Parameters
  def params: FBParams
  val clock: Clock
  val reset: Reset
  
  //val PollReg = Reg(UInt(params.size.W)) Seems very wrong to me
  //Seems like something I would do with ROM described here:
  //https://www.chisel-lang.org/chisel3/docs/explanations/memories.html
  //But does this work with the register mapping?
  
  //Do I even need this class? The whole point is to remap data, no calculations
  //pollrate is equivalent to cycles for 60 frames per second if simulated at 100MHz
  //val impl = Module(new FBMMIO(params.width, params.height, 1660000))
  
  /*Do I just instantiate a Register of size 600*800*2???
  regmap(
    0x00 -> Seq(
      Regfield.r(params.size, )
    )
  )
  */
}

class FBAXI4(params: FBParams, beatBytes: Int)(implicit p: Parameters)
  extends AXI4RegisterRouter(
    params.address,
    beatBytes=beatBytes)(
      new AXI4RegBundle(params, _) with FBTopIO)(
      new AXI4RegModule(params, _, _) with FBModule)

