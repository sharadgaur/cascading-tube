package jj

import cascading.tuple.TupleEntry
import cascading.pipe.Pipe
import org.json4s._
import org.json4s.native.JsonMethods._
import jj.tube.shorthands.{FieldsConversions, OperationShortcuts, SortShortcut}
import scala.collection.convert.WrapAsScala.asScalaIterator
import scala.collection.immutable.TreeMap
import scala.util.Try
import jj.tube.util.TupleEntriesIterator

/**
 * Object containing helper method for operating on input and output of the flow. Incorporating standard conversions between scala structures and cascading.
 */
package object tube extends FieldsConversions with OperationShortcuts with SortShortcut{
  implicit val jsonStandardFormats = DefaultFormats

  implicit def toPipe(tube: Tube) = tube.pipe
  implicit def toTube(pipe: Pipe) = new Tube(pipe)

  type FUNCTION = TupleEntry => List[TupleEntry]
  type SURJECTION = TupleEntry => TupleEntry
  type BUFFER = (TupleEntry, TupleEntriesIterator) => TraversableOnce[TupleEntry]
  type FILTER = TupleEntry => Boolean
  type JOIN = (TupleEntriesIterator, Iterable[TupleEntry]) => TraversableOnce[TupleEntry]

  /**allow easy operations on TupleEntry without allocation **/
  implicit class RichTupleEntry(val tupleEntry: TupleEntry) extends AnyVal {
    def safeGet[T](alias:String)(implicit m: Manifest[T]):Option[T] =
      Try(tupleEntry.getObject(alias,m.runtimeClass).asInstanceOf[T]).toOption.flatMap{ v => Option(v)}

    def safeGet[T](position:Int)(implicit m: Manifest[T]):Option[T] =
      Try(tupleEntry.getObject(position,m.runtimeClass).asInstanceOf[T]).toOption.flatMap{ v => Option(v)}

    def apply(alias:String) = safeGet[String](alias).getOrElse("")
    def apply(position:Int) = safeGet[String](position).getOrElse("")

    def int(alias:String) = safeGet[Int](alias).get
    def int(position:Int) = safeGet[Int](position).get

    def double(alias:String) = safeGet[Double](alias).get
    def double(position:Int) = safeGet[Double](position).get

    def long(alias:String) = safeGet[Long](alias).get
    def long(position:Int) = safeGet[Long](position).get

    def json(alias:String) = parse(apply(alias))
    def json(position:Int) = parse(apply(position))

    def add(value: (String,Any)) = {
      if(Try(tupleEntry.getFields.getPos(value._1)).filter(_ >= 0).isSuccess){
        tupleEntry.setObject(value._1,value._2)
        tupleEntry
      } else{
        tupleEntry.appendNew(
          new TupleEntry(value._1,
                         new cascading.tuple.Tuple(value._2.asInstanceOf[Object])))
      }
    }
    def addAll(extraFields: Map[String,Any]) = extraFields.foldLeft(tupleEntry){
      (te,kv) => te.add(kv._1, kv._2)
    }

    def copy = new TupleEntry(tupleEntry)

    def toSortedMap = TreeMap(toMap.toArray:_*)

    def toMap = tupleEntry.getFields.iterator.map{ i =>
      i.toString -> Option(tupleEntry.getString(i.asInstanceOf[Comparable[_]])).getOrElse("")
    }.toMap
  }
}





