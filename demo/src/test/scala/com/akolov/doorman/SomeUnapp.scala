package com.akolov.doorman

object SomeUnapp extends App {
  object Foo {
    def unapply(f: Foo): Some[Int] = Some(f.v + 1)
  }
  class Foo(val v: Int)
  new Foo(1) match { case Foo(2) => println(1); case _ => println("Something") }
}
