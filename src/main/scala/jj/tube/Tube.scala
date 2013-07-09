package jj.tube

import cascading.pipe._
import cascading.pipe.joiner.{Joiner, InnerJoin}
import cascading.tuple.{TupleEntry, Fields, Tuple}
import cascading.tuple.Fields._
import cascading.operation.Insert
import cascading.pipe.assembly._
import cascading.operation.aggregator.First
import CustomOps._
import Tube._

object Tube {
  def apply(name: String) = new Tube(new Pipe(name))

  def apply(name: String, previous: Pipe) = new Tube(new Pipe(name, previous))

  implicit def toPipe(tube: Tube) = tube.pipe

  implicit def toTube(pipe: Pipe) = new Tube(pipe)
}

class Tube(var pipe: Pipe) extends Grouping with GroupOperator with RowOperator with FieldsTransform with MathOperation {
  def checkpoint = this << new Checkpoint(pipe)

  def merge(tubes: Tube*) = this << new Merge(pipe :: tubes.map(_.pipe).toList: _*)

  def <<(op: Pipe) = {
    pipe = op
    this
  }
}

trait Grouping {
  this: Tube =>

  def aggregateBy(key: Fields, aggregators: AggregateBy*) = this << new AggregateBy(pipe, key, aggregators: _*)

  def groupBy(key: Fields, sort: Fields, reverse: Boolean = false) = this << new GroupBy(pipe, key, sort, reverse)

  def coGroup(leftKey: Fields, rightCollection: Tube, rightKey: Fields, joiner: Joiner = new InnerJoin) = this << new CoGroup(pipe, leftKey, rightCollection, rightKey, joiner)

  def hashJoin(leftKey: Fields, rightCollection: Tube, rightKey: Fields, joiner: Joiner = new InnerJoin) = this << new HashJoin(pipe, leftKey, rightCollection, rightKey, joiner)

  def unique(fields: Fields) = this << new Unique(pipe, fields)
}

trait RowOperator {
  this: Tube =>

  def each(input: Fields = ALL, funcScheme: Fields = UNKNOWN, outScheme: Fields = ALL)(function: (TupleEntry => TupleEntry)) = this << new Each(pipe, input, asFunction(function).setOutputScheme(funcScheme), outScheme)

  def filter(input: Fields = ALL)(filter: TupleEntry => Boolean) = this << new Each(pipe, input, asFilter(filter))
}

trait GroupOperator {
  this: Tube =>

  def every(input: Fields = ALL, bufferScheme: Fields = UNKNOWN, outScheme: Fields = RESULTS)(buffer: (TupleEntry, Iterator[TupleEntry]) => List[TupleEntry]) = this << new Every(pipe, input, asBuffer(buffer).setOutputScheme(bufferScheme), outScheme)

  def top(group: Fields, sort: Fields, reverse: Boolean = false, limit: Int = 1) = {
    groupBy(group, sort, reverse)
    this << new Every(pipe, VALUES, new First(limit))
  }
}

trait FieldsTransform {
  this: Tube =>

  def discard(field: Fields) = this << new Discard(pipe, field)

  def rename(from: Fields, to: Fields) = this << new Rename(pipe, from, to)

  def retain(fields: Fields) = this << new Retain(pipe, fields)

  def insert(field: Fields, value: String*) = this << new Each(pipe, new Insert(field, value: _*), ALL)
}

trait MathOperation {
  this: Tube =>
  def divide(leftOp: String, rightOp: String, outField: String) = op(leftOp, rightOp, outField) {
    _ / _
  }

  def multiply(leftOp: String, rightOp: String, outField: String) = op(leftOp, rightOp, outField) {
    _ * _
  }

  def plus(leftOp: String, rightOp: String, outField: String) = op(leftOp, rightOp, outField) {
    _ + _
  }

  def minus(leftOp: String, rightOp: String, outField: String) = op(leftOp, rightOp, outField) {
    _ - _
  }

  def op[T](leftOp: String, rightOp: String, outField: String)(func: (Double, Double) => T) = {
    this << each((leftOp, rightOp), outField) {
      row: TupleEntry =>
        val tuple = new TupleEntry(outField)
        val result = func(row.getDouble(leftOp), row.getDouble(rightOp))
        result match {
          case d: Double => tuple.setDouble(outField, d)
          case i: Int => tuple.setInteger(outField, i)
          case l: Long => tuple.setLong(outField, l)
        }
        tuple
    }
  }
}
