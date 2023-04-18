package org.squeryl.test

import org.squeryl._
import org.squeryl.framework._
import org.squeryl.test.PrimitiveTypeModeForTests._
import org.squeryl.internals.Utils

class Foo(val value: String) extends KeyedEntity[Long] {
  val id: Long = 0
}

object FooSchema extends Schema {
  val foos = table[Foo]()

  def reset() = {
    drop // its protected for some reason
    create
  }
}

abstract class TransactionTests extends DbTestBase {
  self: DBConnector =>
  // repeat the import closer to call site to give priority to our `===` operator
  import org.squeryl.test.PrimitiveTypeMode4Tests._

  def throwExc(except: Boolean): Int = {
    if (except) throw new Exception()
    1
  }

  def doSomething(except: Boolean): Int = {
    transaction {
      throwExc(except)
    }
  }

  def returnInTransaction: Int =
    transaction {
      FooSchema.foos.insert(new Foo("test"))
      return 1
    }

  test("No exception in transaction") {
    transaction {
      FooSchema.reset()
    }
    transaction {
      FooSchema.foos.insert(new Foo("test"))
      assert(FooSchema.foos.where(f => f.value === "test").size == 1)

      try {
        doSomething(true)
      } catch {
        case e: Exception => {}
      }

      // fails with "no session exception"
      assert(FooSchema.foos.where(f => f.value === "test").size == 1)
    }
  }

  test("Returning in transaction") {
    transaction {
      FooSchema.reset()
    }
    transaction {
      FooSchema.foos.insert(new Foo("test"))
      assert(FooSchema.foos.where(f => f.value === "test").size == 1) // should equal(1)

      doSomething(false)
      // fails with "no session exception"
      assert(FooSchema.foos.where(f => f.value === "test").size == 1) // should equal(1)
    }
  }

  test("Returning out of transaction") {
    transaction {
      FooSchema.reset()
    }
    transaction {
      FooSchema.foos.insert(new Foo("test"))
      assert(FooSchema.foos.where(f => f.value === "test").size == 1) // should equal(1)

      doSomething(false)
    }
    transaction {
      // works!
      assert(FooSchema.foos.where(f => f.value === "test").size == 1) // should equal(1)
    }
  }

  test("Returning inside transaction block") {
    transaction {
      FooSchema.reset()
    }
    returnInTransaction
    transaction {
      // works!
      assert(FooSchema.foos.where(f => f.value === "test").size == 1) // should equal(1)
    }
  }

  test("nested transactions with SessionFactory") {

    val sf1 = new SessionFactory {
      def newSession: AbstractSession = sessionCreator().get()
    }

    val sf2 = new SessionFactory {
      def newSession: AbstractSession = Utils.throwError("inner inTransaction should not be started")
    }

    inTransaction(sf1) {

      inTransaction(sf2) {}
    }
  }

}
