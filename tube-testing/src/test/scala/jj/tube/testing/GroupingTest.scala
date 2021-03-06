package jj.tube.testing

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.Matchers
import jj.tube._
import jj.tube.testing.BaseFlowTest.Source

@RunWith(classOf[JUnitRunner])
class GroupingTest extends FunSuite with BaseFlowTest with Matchers {
  test("word count with alphabetic order of result"){
    //given
    val srcWords = Source("w", List("dog","cat","dog", "cat", "cat", "avocado"))

    //when
    val inputWords = Tube("words")
      .groupBy("w"){ (group, row) =>

        val count = row.count(_ => true)
        Map("w" -> group("w"), "c" -> count)
      }.declaring("w","c")
      .retain("w","c")

    //then
    runFlow
      .withSource(inputWords, srcWords)
      .withOutput(inputWords, {
        _ should contain only("dog,2", "cat,3", "avocado,1")
      }).compute
  }

  test("should group by and sort group with ascending name and rewriting current (in iterator context) tuple"){
    //given
    val srcWords = Source(("id","w"), List(("1","b"),("1","a"),("1","c")))

    //when
    val inputWords = Tube("words")
      .groupBy("id").sorted(ASC("w")){ (group, row) =>
        row.next()
        row.saveCurrentTupleEntry()
      }.declaring("w")
      .go

    //then
    runFlow
      .withSource(inputWords, srcWords)
      .withOutput(inputWords, {
        _ should contain only "a"
      }).compute
  }

  test("list number of childs for each parent with one coGroup operation"){
    //given
    val srcParent = Source(("name","id_parent"), List(("joe","1"),("carol","2")))
    val srcChildren = Source("id_parent", List("1","1","1","2","2"))

    //when
    val inputParents = Tube("parents")
    val inputChildren = Tube("children")
    val ageOfOldestChildPerParent = Tube("ageOfOldestChildPerParent",inputChildren)
      .coGroup(inputParents).on("id_parent","id_parent").withJoinFields("parent","name","id") { (group, row) =>
        val firstChild = row.next()
        List(
          Map("name" -> firstChild("name"),"no"->(1 +row.count( _ => true)))
        )
      }.declaring("name","no")

    //then
    runFlow
      .withSource(inputParents, srcParent)
      .withSource(inputChildren, srcChildren)
      .withOutput(ageOfOldestChildPerParent, {
        _ should contain only("joe,3", "carol,2")
      }).compute
  }

  test("should get top for each group sorted ascending"){
    //given
    val srcWords = Source(("gr","num"), List(("1","1"),("1","2")))

    //when
    val inputWords = Tube("words")
      .top("gr", ASC("num"))

    //then
    runFlow
      .withSource(inputWords, srcWords)
      .withOutput(inputWords, {
      _ should contain only "1,1"
    }).compute
  }

  test("should get top for each group sorted descending"){
    //given
    val srcWords = Source(("gr","num"), List(("1","2"),("1","1")))

    //when
    val inputWords = Tube("words")
      .top("gr", DESC("num"))

    //then
    runFlow
      .withSource(inputWords, srcWords)
      .withOutput(inputWords, {
      _ should contain only "1,2"
    }).compute
  }
}
