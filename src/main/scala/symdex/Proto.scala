package symdex

import java.nio.charset.StandardCharsets.UTF_8

/**
 * The protobuf wire format, as much of it as SemanticDB uses: varints,
 * length-delimited fields, and skipping whatever else is there. A
 * reader is a window `[from, until)` over one byte array, so a nested
 * message is a narrower window over the same bytes — nothing is copied.
 */
final class Proto(buf: Array[Byte], from: Int, until: Int):
  private var pos = from

  def hasMore: Boolean = pos < until

  /** the next field's (number, wire type) */
  def field(): (Int, Int) =
    val t = varint().toInt
    (t >>> 3, t & 7)

  def varint(): Long =
    var result = 0L
    var shift = 0
    var more = true
    while more do
      val b = buf(pos)
      pos += 1
      result |= (b & 0x7f).toLong << shift
      shift += 7
      more = (b & 0x80) != 0
    result

  def int(): Int = varint().toInt

  def message(): Proto =
    val n = int()
    val p = new Proto(buf, pos, pos + n)
    pos += n
    p

  def string(): String =
    val n = int()
    val s = String(buf, pos, n, UTF_8)
    pos += n
    s

  def skip(wire: Int): Unit = wire match
    case 0 => val _ = varint()
    case 1 => pos += 8
    case 2 =>
      // the length first: `pos += int()` reads pos BEFORE int() moves it
      val n = int()
      pos += n
    case 5 => pos += 4
    case w => throw IllegalArgumentException(s"protobuf: unsupported wire type $w at $pos")

object Proto:
  def apply(bytes: Array[Byte]): Proto = new Proto(bytes, 0, bytes.length)
