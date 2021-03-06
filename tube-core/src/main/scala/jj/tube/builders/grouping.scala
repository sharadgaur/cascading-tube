package jj.tube.builders

import cascading.pipe.{CoGroup, Every, GroupBy}
import jj.tube._
import cascading.tuple.Fields
import cascading.tuple.Fields._
import scala.language.{reflectiveCalls, existentials}
import cascading.operation.{BufferCall, Buffer, BaseOperation}
import cascading.flow.FlowProcess
import jj.tube.SortOrder
import jj.tube.util.TupleEntriesIterator
import scala.collection.convert.WrapAsScala.asScalaIterator

trait BaseGroupingBuilder extends OperationBuilder {
  def every(baseStream: Tube, inputScheme: Fields, operation: BUFFER, declaringScheme: Fields, resultScheme: Fields) =
    new Every(baseStream, inputScheme, asBuffer(operation).setOutputScheme(declaringScheme), resultScheme)

  def asBuffer(transform: BUFFER) =
    new BaseOperation[Any] with Buffer[Any] {
      override def operate(flowProcess: FlowProcess[_], bufferCall: BufferCall[Any]) {
        transform(bufferCall.getGroup, TupleEntriesIterator(bufferCall.getArgumentsIterator, bufferCall.getOutputCollector))
          .foreach(WithCustomOperation.writeTupleEntryToOutput(_, bufferCall.getOutputCollector))
      }

      def setOutputScheme(field: Fields) = {
        fieldDeclaration = field
        this
      }
    }
}

class GroupingBuilder(val baseStream: Tube) extends BaseGroupingBuilder
with WithCustomOperation[GroupingBuilder, BUFFER]
with WithOperationResult[GroupingBuilder] {

  withInput(ALL)
  declaring(UNKNOWN)
  withResult(RESULTS)

  var order: SortOrder = NO_SORT().asInstanceOf[SortOrder]
  var keys: Fields = null

  def on(keys: Fields) = {
    this.keys = keys; this
  }

  /**
   * imply sort of input for each transformation according to fields and direction
   */
  def sorted[T <: SortOrder](order: T) = {
    this.order = order
    this
  }

  def go =
    baseStream << {
      new GroupBy(baseStream, keys, order.sortedFields, false)
    } << every(baseStream, inputScheme, operation, declaringScheme, resultScheme)
}

class CoGroupingBuilder(val leftStream: Tube, val rightStream: Tube) extends BaseGroupingBuilder
with JoinApply[CoGroupingBuilder]
with WithJoinStrategy[CoGroupingBuilder]
with WithCustomOperation[CoGroupingBuilder, BUFFER]
with WithOperationResult[CoGroupingBuilder] {

  var joinFields: Fields = null

  def withJoinFields(alter: Fields) = {
    this.joinFields = alter; this
  }

  withInput(ALL)
  declaring(UNKNOWN)
  withResult(RESULTS)

  override def on(keys: Fields) = {
    leftStreamKey = keys
    rightStreamKey = keys
    this
  }

  override def go =
    leftStream << {
      new CoGroup(leftStream, leftStreamKey, rightStream, rightStreamKey, joinFields, joinStrategy)
    } << every(leftStream, inputScheme, operation, declaringScheme, resultScheme)
}
