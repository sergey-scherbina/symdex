package fixture

/** A shape has an area. */
trait Shape:
  def area: Double

final case class Square(side: Double) extends Shape:
  def area: Double = side * side

final case class Circle(r: Double) extends Shape:
  def area: Double = math.Pi * r * r

abstract class Polygon extends Shape:
  def sides: Int
  def label: String = s"$sides sides"

final class Triangle(b: Double, h: Double) extends Polygon:
  def sides: Int = 3
  def area: Double = b * h / 2

trait Show[A]:
  def show(a: A): String

object Show:
  given Show[Int] with
    def show(a: Int): String = s"int $a"
  given showShape: Show[Shape] = s => s"shape ${s.area}"

extension (s: Shape) def doubled: Double = s.area * 2

object Use:
  def render[A](a: A)(using sh: Show[A]): String = sh.show(a)

  def total(shapes: List[Shape]): Double = shapes.map(_.area).sum

  def unit: Shape = new Shape { def area: Double = 1 }

  def report(): String =
    val shapes = List(Square(2), Circle(1), Triangle(3, 4))
    render(1) + render(shapes.head) + total(shapes) + shapes.head.doubled
