package fixture.other

import fixture.{Square, Use}

object Other:
  def big: Double = Use.total(List(Square(10)))
