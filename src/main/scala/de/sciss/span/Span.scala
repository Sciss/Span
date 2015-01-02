/*
 *  Span.scala
 *  (Span)
 *
 *  Copyright (c) 2013-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.span

import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer, Writable}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

object Span {
  def from (start: Long): From  = From (start)
  def until(stop : Long): Until = Until(stop )

  def apply  (start: Long, stop: Long): Span    = Apply(start, stop)
  def unapply(span: Span): Option[(Long, Long)] = Some((span.start, span.stop))

  def all : All .type = All
  def void: Void.type = Void

  implicit object serializer extends ImmutableSerializer[Span] {
    def write(v: Span, out: DataOutput): Unit = v.write(out)

    def read(in: DataInput): Span = Span.read(in)
  }

  def read(in: DataInput): Span = {
    val cookie = in.readByte()
    if (cookie != 0) sys.error(s"Unexpected cookie $cookie")
    Span(in.readLong(), in.readLong())
  }

  sealed trait NonVoid          extends SpanLike {
    def shift    (delta: Long)    : NonVoid
  }
  sealed trait Bounded          extends NonVoid {
    def shift    (delta: Long)    : Bounded
  }
  sealed trait FromOrAll        extends Open     {
    def shift    (delta: Long)    : FromOrAll
    def nonEmptyOption            : Option[FromOrAll]
  }
  sealed trait HasStartOrVoid   extends SpanLike {
    def shift    (delta: Long)    : HasStartOrVoid
    def nonEmptyOption            : Option[HasStart]
  }
  sealed trait UntilOrAll       extends Open     {
    def shift    (delta: Long)    : UntilOrAll
    def nonEmptyOption            : Option[UntilOrAll]
  }
  sealed trait HasStopOrVoid    extends SpanLike {
    def shift    (delta: Long)    : HasStopOrVoid
    def nonEmptyOption            : Option[HasStop]
  }
  sealed trait SpanOrVoid       extends HasStartOrVoid with HasStopOrVoid {

    /** The span's length. For a void span, this is zero, otherwise it is `stop - start`. */
    def length: Long

    def shift    (delta: Long)    : SpanOrVoid
    def intersect(that: SpanLike) : SpanOrVoid
    def subtract (that: Span.Open): SpanOrVoid
    def subtract (that: SpanLike) : Vec[SpanOrVoid]

    def nonEmptyOption: Option[Span]
  }

  object HasStart {
    def unapply(span: HasStart): Option[Long] = Some(span.start)
  }

  sealed trait HasStart extends Bounded with HasStartOrVoid {
    /** @return  the start position of the span. this is considered included in the interval */
    def start: Long

    def shift(delta: Long): HasStart

    final def compareStart(pos: Long) = if (start < pos) -1 else if (start > pos) 1 else 0

    def intersect(that: SpanLike) : HasStartOrVoid
    def subtract (that: Span.Open): HasStartOrVoid
    def subtract (that: SpanLike) : Vec[HasStart]
  }

  object HasStop {
    def unapply(span: HasStop): Option[Long] = Some(span.stop)
  }

  sealed trait HasStop extends Bounded with HasStopOrVoid {
    /** @return  the stop position of the span. this is considered excluded in the interval */
    def stop: Long

    def shift(delta: Long): HasStop

    final def compareStop(pos: Long) = if (stop < pos) -1 else if (stop > pos) 1 else 0
  }

  sealed trait Open extends NonVoid {
    final def isEmpty  = false
    final def nonEmpty = true

    def shift(delta: Long)   : Open
    def union(that: SpanLike): Open
    def invert: SpanLike
  }

  case object All extends FromOrAll with UntilOrAll {
    def nonEmptyOption: Option[All.type] = Some(this)

    def shift(delta: Long): All.type = this

    def union(that: SpanLike): All.type = this

    def intersect(that: SpanLike): SpanLike = that

    def clip(pos: Long): Long = pos

    def invert: Void.type = Void

    def compareStart(pos: Long) = -1
    def compareStop (pos: Long) = 1

    def contains(pos: Long) = true

    def contains(that: SpanLike) = that != Void

    def overlaps(that: SpanLike) = that match {
      case sp: Span => sp.nonEmpty
      case Void     => false
      case _        => true
    }

    def touches(that: SpanLike) = that != Void

    def subtract(that: Span.Open): SpanLike = that.invert

    def subtract(that: SpanLike): Vec[SpanLike] = that match {
      case Span(start, stop)  => Vec(Until(start), From(stop))
      case From(start)        => Vec(Until(start))
      case Until(stop)        => Vec(From(stop))
      case All                => Vec.empty
      case Void               => Vec(this)
    }

    def write(out: DataOutput): Unit = out.writeByte(3)
  }

  final case class From(start: Long) extends FromOrAll with HasStart {
    def nonEmptyOption: Option[From] = Some(this)

    def clip(pos: Long): Long = math.max(start, pos)

    def shift(delta: Long): From = From(start + delta)

    def invert: Until = Until(start)

    def compareStop(pos: Long) = 1

    def contains(pos: Long): Boolean = pos >= start

    def contains(that: SpanLike): Boolean = that match {
      case From(thatStart)    => thatStart >= start
      case Span(thatStart, _) => thatStart >= start
      case _ => false
    }

    def overlaps(that: SpanLike): Boolean = that match {
      case From(_)            => true
      case Until(thatStop)    => start < thatStop
      case Span(_, thatStop)  => start < thatStop
      case Void               => false
      case All                => true
    }

    def touches(that: SpanLike): Boolean = that match {
      case From(_)            => true
      case Until(thatStop)    => start <= thatStop
      case Span(_, thatStop)  => start <= thatStop
      case Void               => false
      case All                => true
    }

    def union(that: SpanLike): FromOrAll = that match {
      case From(thatStart)    => From(math.min(start, thatStart))
      case Span(thatStart, _) => From(math.min(start, thatStart))
      case Void               => this
      case _                  => All
    }

    def intersect(that: SpanLike): HasStartOrVoid = that match {
      case From(thatStart) => From(math.max(start, thatStart))
      case Until(thatStop) => if (start <= thatStop) Span(start, thatStop) else Void
      case Span(thatStart, thatStop) =>
        val maxStart = math.max(start, thatStart)
        if (maxStart <= thatStop) Span(maxStart, thatStop) else Void
      case Void => Void
      case All  => this
    }

    def subtract(that: Span.Open): HasStartOrVoid = that match {
      case Span.From(thatStart) =>
        if (thatStart >= start) Span(start, thatStart) else Span.Void
      case Span.Until(thatStop) if thatStop > start => From(thatStop)
      case Span.All => Span.Void
      case _        => this
    }

    def subtract(that: SpanLike): Vec[HasStart] = that match {
      case Span.From(thatStart) =>
        if (thatStart > start) Vec(Span(start, thatStart)) else Vec.empty
      case Span.Until(thatStop) if thatStop > start => Vec(From(thatStop))
      case Span(thatStart, thatStop) if thatStop > start =>
        if (thatStart <= start) {
          Vec(From(thatStop))
        } else {
          Vec(Span(start, thatStart), From(thatStop))
        }
      case Span.All => Vec.empty
      case _        => Vec(this)
    }

    def write(out: DataOutput): Unit = {
      out.writeByte(1)
      out.writeLong(start)
    }
  }

  final case class Until(stop: Long) extends UntilOrAll with HasStop {
    def nonEmptyOption: Option[Until] = Some(this)

    def clip(pos: Long): Long = math.min(stop, pos)

    def shift(delta: Long): Until = Until(stop + delta)

    def invert: From = From(stop)

    def compareStart(pos: Long) = -1

    def contains(pos: Long): Boolean = pos < stop

    def contains(that: SpanLike): Boolean = that match {
      case Until(thatStop)    => thatStop <= stop
      case Span(_, thatStop)  => thatStop <= stop
      case _                  => false
    }

    def overlaps(that: SpanLike): Boolean = that match {
      case Until(_)           => true
      case From(thatStart)    => thatStart < stop
      case Span(thatStart, _) => thatStart < stop
      case Void               => false
      case All                => true
    }

    def touches(that: SpanLike): Boolean = that match {
      case Until(_)           => true
      case From(thatStart)    => thatStart <= stop
      case Span(thatStart, _) => thatStart <= stop
      case Void               => false
      case All                => true
    }

    def union(that: SpanLike): UntilOrAll = that match {
      case Until(thatStop)    => Until(math.max(stop, thatStop))
      case Span(_, thatStop)  => Until(math.max(stop, thatStop))
      case Void               => this
      case _                  => All
    }

    def intersect(that: SpanLike): HasStopOrVoid = that match {
      case From(thatStart) => if (thatStart <= stop) Span(thatStart, stop) else Void
      case Until(thatStop) => Until(math.min(stop, thatStop))
      case Span(thatStart, thatStop) =>
        val minStop = math.min(stop, thatStop)
        if (thatStart <= minStop) Span(thatStart, minStop) else Void
      case Void => Void
      case All  => this
    }

    def subtract(that: Span.Open): HasStopOrVoid = that match {
      case Span.From(thatStart) if thatStart < stop => Until(thatStart)
      case Span.Until(thatStop) =>
        if (thatStop <= stop) Span(thatStop, stop) else Span.Void
      case Span.All => Span.Void
      case _        => this
    }

    def subtract(that: SpanLike): Vec[HasStop] = that match {
      case Span.From(thatStart) if thatStart < stop => Vec(Until(thatStart))
      case Span.Until(thatStop) =>
        if (thatStop < stop) Vec(Span(thatStop, stop)) else Vec.empty
      case Span(thatStart, thatStop) if thatStart < stop =>
        if (thatStop >= stop) {
          Vec(Until(thatStart))
        } else {
          Vec(Until(thatStart), Span(thatStop, stop))
        }
      case Span.All => Vec.empty
      case _        => Vec(this)
    }

    def write(out: DataOutput): Unit = {
      out.writeByte(2)
      out.writeLong(stop)
    }
  }

  case object Void extends SpanOrVoid {
    final val length = 0L

    def nonEmptyOption: Option[Span] = None

    def shift(delta: Long): Void.type = this

    def union(that: SpanLike): SpanLike = that

    def invert: All.type = All

    def intersect(that: SpanLike ): Void.type = this
    def subtract (that: Span.Open): Void.type = this

    def subtract(that: SpanLike): Vec[SpanOrVoid] = Vec.empty

    def clip(pos: Long): Long = pos

    def compareStart(pos: Long) = 1
    def compareStop (pos: Long) = -1

    def contains(pos: Long) = false

    def contains(that: SpanLike) = false
    def overlaps(that: SpanLike) = false
    def touches (that: SpanLike) = false

    val isEmpty  = true
    val nonEmpty = false

    def write(out: DataOutput): Unit = out.writeByte(4)
  }

  private final case class Apply(start: Long, stop: Long) extends Span {
    if (start > stop) throw new IllegalArgumentException(s"A span's start ($start) must be <= its stop ($stop)")

    def nonEmptyOption: Option[Span] = if (start < stop) Some(this) else None

    override def toString = s"Span($start,$stop)"

    def length: Long = stop - start

    def contains(pos: Long) = pos >= start && pos < stop

    def shift(delta: Long): Span = Span(start + delta, stop + delta)

    def clip(pos: Long): Long = math.max(start, math.min(stop, pos))

    def isEmpty : Boolean = start == stop
    def nonEmpty: Boolean = start != stop

    def contains(that: SpanLike): Boolean = that match {
      case Span(thatStart, thatStop) => (thatStart >= start) && (thatStop <= stop)
      case _ => false
    }

    def union(that: SpanLike): SpanLike = that match {
      case Span.From (thatStart)      => Span.From (math.min(start, thatStart))
      case Span.Until(thatStop)       => Span.Until(math.max(stop,  thatStop))
      case Span(thatStart, thatStop)  => Span(math.min(start, thatStart), math.max(stop, thatStop))
      case Span.Void                  => this
      case Span.All                   => Span.All
    }

    def union(that: Span): Span = Span(math.min(start, that.start), math.max(stop, that.stop))

    def intersect(that: SpanLike): SpanOrVoid = that match {
      case Span.From(thatStart) =>
        val maxStart = math.max(start, thatStart)
        if (maxStart <= stop) Span(maxStart, stop) else Span.Void
      case Span.Until(thatStop) =>
        val minStop = math.min(stop, thatStop)
        if (start <= minStop) Span(start, minStop) else Span.Void
      case Span(thatStart, thatStop) =>
        val maxStart = math.max(start, thatStart)
        val minStop = math.min(stop, thatStop)
        if (maxStart <= minStop) Span(maxStart, minStop) else Span.Void
      case Span.Void  => Span.Void
      case Span.All   => this
    }

    def overlaps(that: SpanLike): Boolean = that match {
      case Span.From(thatStart) =>
        val maxStart = math.max(start, thatStart)
        maxStart < stop
      case Span.Until(thatStop) =>
        val minStop = math.min(stop, thatStop)
        start < minStop
      case Span(thatStart, thatStop) =>
        val maxStart = math.max(start, thatStart)
        val minStop = math.min(stop, thatStop)
        maxStart < minStop
      case Span.Void  => false
      case Span.All   => nonEmpty
    }

    def touches(that: SpanLike): Boolean = that match {
      case Span.From(thatStart) =>
        val maxStart = math.max(start, thatStart)
        maxStart <= stop
      case Span.Until(thatStop) =>
        val minStop = math.min(stop, thatStop)
        start <= minStop
      case Span(thatStart, thatStop) =>
        val maxStart = math.max(start, thatStart)
        val minStop = math.min(stop, thatStop)
        maxStart <= minStop
      case Span.Void  => false
      case Span.All   => true
    }

    def subtract(that: Span.Open): Span.SpanOrVoid = that match {
      case Span.From(thatStart) if thatStart < stop =>
        if (thatStart >= start) Span(start, thatStart) else Span.Void
      case Span.Until(thatStop) if thatStop > start =>
        if (thatStop <= stop) Span(thatStop, stop) else Span.Void
      case Span.All => Span.Void
      case _        => this
    }

    def subtract(that: SpanLike): Vec[Span] = that match {
      case Span.From(thatStart) if thatStart < stop =>
        if (thatStart > start) Vector(Span(start, thatStart)) else Vec.empty
      case Span.Until(thatStop) if thatStop > start =>
        if (thatStop < stop) Vector(Span(thatStop, stop)) else Vec.empty
      case Span(thatStart, thatStop) if thatStart < stop && thatStop > start =>
        if (thatStart <= start) {
          if (thatStop < stop) Vector(Span(thatStop, stop)) else Vec.empty
        } else if (thatStop >= stop) {
          Vector(Span(start, thatStart))
        } else {
          Vector(Span(start, thatStart), Span(thatStop, stop))
        }
      case Span.All => Vector.empty
      case _        => if (isEmpty) Vector.empty else Vector(this)
    }

    def write(out: DataOutput): Unit = {
      out.writeByte(0)
      out.writeLong(start)
      out.writeLong(stop)
    }
  }
}

object SpanLike {
  implicit object serializer extends ImmutableSerializer[SpanLike] {
    def write(v: SpanLike, out: DataOutput): Unit = v.write(out)

    def read(in: DataInput): SpanLike = SpanLike.read(in)
  }

  def read(in: DataInput): SpanLike = (in.readByte(): @switch) match {
    case 0 => Span(in.readLong(), in.readLong())
    case 1 => Span.from(in.readLong())
    case 2 => Span.until(in.readLong())
    case 3 => Span.All
    case 4 => Span.Void
    case cookie => sys.error(s"Unrecognized cookie $cookie")
  }
}

sealed trait SpanLike extends Writable {
  /** Clips a position to this span's boundary. Note that
    * the span's stop position is included. Thus the result
    * is greater than or equal the start, and less than or equal (!)
    * the stop.
    *
    * For the special cases of `Span.All` and `Span.Void`, this
    * method returns the argument unchanged.
    *
    * @param pos  the point to clip
    * @return     the clipped point
    */
  def clip(pos: Long): Long

  /** Shifts the span, that is applies an offset to its start and stop.
    * For single sided open spans (`Span.From` and `Span.Until`) this
    * alters the only bounded value. For `Span.All` and `Span.Void`
    * this returns the object unchanged.
    *
    * @param delta   the shift amount (the amount to be added to the span's positions)
    * @return  the shifted span
    */
  def shift(delta: Long): SpanLike

  /** Checks if the span is empty. A span is empty if it is
    * a `Span` with `start == stop` or if it is void.
    *
    * @return		<code>true</code>, if <code>start == stop</code>
    */
  def isEmpty: Boolean

  /** Checks if the span is non empty. This is exactly the opposite value of `isEmpty`. */
  def nonEmpty: Boolean

  /** Checks if a position lies within the span. Note that this returns
    * `false` if `this.stop == pos`.
    *
    * @return		<code>true</code>, if <code>start <= pos < stop</code>
    */
  def contains(pos: Long): Boolean

  /** Compares the span's start to a given position
    *
    * @return		<code>-1</code>, if the span start lies before the query position,
    *            <code>1</code>, if it lies after that position, or
    *            <code>0</code>, if both are the same
    */
  def compareStart(pos: Long): Int

  /** Compares the span's stop to a given position
    *
    * @return		<code>-1</code>, if the span stop lies before the query position,
    *            <code>1</code>, if it lies after that position, or
    *            <code>0</code>, if both are the same
    */
  def compareStop(pos: Long): Int

  /** Checks if another span lies within the span. The result is `false`
    * if either of the two spans is void.
    *
    * @param	that	second span
    * @return		`true`, if `that.start >= this.start && that.stop <= this.stop`
    */
  def contains(that: SpanLike): Boolean

  /** Checks if a two spans overlap each other. Two spans overlap if the overlapping area
    * is greater than or equal to 1. This implies that if either span is empty, the result
    * will be `false`.
    *
    * This method is commutative (`a overlaps b == b overlaps a`).
    *
    * @param	that	second span
    * @return		<code>true</code>, if the spans
    *            overlap each other
    */
  def overlaps(that: SpanLike): Boolean

  /** Checks if a two spans overlap or touch each other. Two spans touch each other if
    * they either overlap or they share a common point with each other (this span's start or stop
    * is that span's start or stop).
    *
    * This method is commutative (`a touches b == b touches a`).
    *
    * @param	that	second span
    * @return	`true`, if the spans touch each other
    */
  def touches(that: SpanLike): Boolean

  /** Constructs a single span which contains both `this` and `that` span. If the two spans
    * are disjoint, the result will be a span with `start = min(this.start, that.start)` and
    * `stop = max(this.stop, that.stop)`. If either span is void, the other span is returned.
    * If either span is `Span.All`, `Span.All` will be returned.
    *
    * This method is commutative (`a union b == b union a`).
    *
    * @param that the span to form the union with
    * @return  the encompassing span
    */
  def union(that: SpanLike): SpanLike

  /** Construct the intersection between this and another span. If the two spans are
    * disjoint, the result will be empty. An empty result may be a `Span` if the two spans
    * touched each other, or `Span.Void` if they did not touch each other. If either span is
    * `Span.All`, the other span is returned. If either span is void, `Span.Void` will be
    * returned.
    *
    * This method is commutative (`a intersect b == b intersect a`).
    *
    * @param that the span to form the intersection with
    * @return  the intersection span (possibly empty)
    */
  def intersect(that: SpanLike): SpanLike

  /** Subtracts a given span from this span. Note that an empty span argument "cuts" this span,
    * e.g. `Span.all subtract Span(30,30) == Seq(Span.until(30),Span.from(30))`
    *
    * @param that the span to subtract
    * @return  a collection of spans after the argument was subtracted. Unlike `intersect`, this method
    *          filters out empty spans, thus a span subtracted from itself produces an empty collection.
    *          if `that` is a `Span`, the result might be two disjoint spans.
    */
  def subtract(that: SpanLike): Vec[SpanLike]

  /** Subtracts a given open span from this span.
    *
    * @param that the span to subtract
    * @return the reduced span, possibly empty or void
    */
  def subtract(that: Span.Open): SpanLike

  def nonEmptyOption: Option[Span.NonVoid]
}

sealed trait Span extends Span.SpanOrVoid with Span.HasStart with Span.HasStop {
  def shift(delta: Long): Span

  def intersect(that: SpanLike): Span.SpanOrVoid

  def subtract(that: Span.Open): Span.SpanOrVoid

  def subtract(that: SpanLike): Vec[Span]

  def union(that: Span): Span
}