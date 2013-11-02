package org.json4s.macroimpls

import language.experimental.macros
import scala.reflect.macros.Context
import java.util.Date

import org.json4s.{Formats, JsonWriter, JValue}
import macrohelpers._
import org.json4s.io.{BufferRecycler, SegmentedStringWriter}


// Intended to be the serialization side of the class builder
object Serializer {

  // Makes the code generated by the macros significantly less cumbersome
  class WriterStack(var current: JsonWriter[_]) {
    def startArray() = { current = current.startArray() }
    def endArray() = { current = current.endArray() }
    def startObject() = { current = current.startObject() }
    def endObject() = { current = current.endObject() }

    def int(in: Int) = { current = current.int(in) }
    def string(in: String) = { current = current.string(in) }
    def float(in: Float) = { current = current.float(in) }
    def double(in: Double) = { current = current.double(in) }
    def bigDecimal(in: BigDecimal) = { current = current.bigDecimal(in) }
    def short(in: Short) = { current = current.short(in) }
    def bigInt(in: BigInt) = { current = current.bigInt(in) }
    def byte(in: Byte) = { current = current.byte(in) }
    def long(in: Long) = { current = current.long(in) }
    def boolean(in: Boolean) = { current = current.boolean(in) }

    def startField(name: String) = { current = current.startField(name) }
    def addJValue(jv: JValue) = { current = current.addJValue(jv) }

    def result = current.result
  }


  def decompose[U: c.WeakTypeTag](c: Context)(obj: c.Expr[U])(defaultFormats: c.Expr[Formats]): c.Expr[JValue] = {
    import c.universe._
    reify {
      val writer = if (defaultFormats.splice.wantsBigDecimal) JsonWriter.bigDecimalAst else JsonWriter.ast
      (serializeImpl(c)(obj, c.Expr[JsonWriter[JValue]](Ident("writer")))(defaultFormats)).splice
      writer.result
    }
  }


  def serializeToStreamWriter[U: c.WeakTypeTag, W <: java.io.Writer](c: Context)(obj: c.Expr[U], w: c.Expr[W])(defaultFormats: c.Expr[Formats]): c.Expr[W] = {
    import c.universe._
    reify {
      val writer = JsonWriter.streaming(w.splice)
      (serializeImpl(c)(obj, c.Expr[JsonWriter[W]](Ident("writer")))(defaultFormats)).splice
      writer.result
    }
  }

  def serializeToString[U: c.WeakTypeTag](c: Context)(obj: c.Expr[U])(defaultFormats: c.Expr[Formats]): c.Expr[String] = {
    import c.universe._
    reify {
      val sw = new SegmentedStringWriter(new BufferRecycler)
      try {
        val writer = new macrohelpers.FastTextWriter(sw)
        (serializeImpl(c)(obj, c.Expr[JsonWriter[SegmentedStringWriter]](Ident("writer")))(defaultFormats)).splice
        writer.result
      } finally {
        sw.close()
      }
    }
  }

  def decomposeWithBuilder[U, T](obj: U, builder: JsonWriter[T])(implicit formats: Formats) = macro decomposeWithBuilder_impl[U,T]
  def decomposeWithBuilder_impl[U: c.WeakTypeTag, T](c: Context)(obj: c.Expr[U], builder: c.Expr[JsonWriter[T]])
                                                    (formats: c.Expr[Formats]): c.Expr[T] = {
    import c.universe._
    reify{
      serializeImpl(c)(obj,builder)(formats).splice
      builder.splice.result
    }
  }


  def serializePrettyToStreamWriter[U: c.WeakTypeTag, W <: java.io.Writer](c: Context)(obj: c.Expr[U], w: c.Expr[W])(defaultFormats: c.Expr[Formats]): c.Expr[W] = {
    import c.universe._
    reify {
      val writer = JsonWriter.streamingPretty(w.splice)
      (serializeImpl(c)(obj, c.Expr[JsonWriter[W]](Ident("writer")))(defaultFormats)).splice
      writer.result
    }
  }

  def serializePrettyToString[U: c.WeakTypeTag](c: Context)(obj: c.Expr[U])(defaultFormats: c.Expr[Formats]): c.Expr[String] = {
    import c.universe._
    reify {
      val sw = new SegmentedStringWriter(new BufferRecycler)
      try {
        val writer = JsonWriter.streamingPretty(sw)
        (serializeImpl(c)(obj, c.Expr[JsonWriter[SegmentedStringWriter]](Ident("writer")))(defaultFormats)).splice
        writer.result.getAndClear
      } finally {
        sw.close()
      }
    }
  }


  /* ----------------- Macro Serializer ----------------- */
  def serialize[U](obj: U, writer: JsonWriter[_])(implicit defaultFormats: Formats) = macro serializeImpl[U]
  def serializeImpl[U: c.WeakTypeTag](c: Context)(obj: c.Expr[U], writer: c.Expr[JsonWriter[_]])
                           (defaultFormats: c.Expr[Formats]): c.Expr[Unit] = {
                      
    import c.universe._
    val helpers = new macrohelpers.MacroHelpers[c.type](c)
    import helpers._

    val Block(writerStackDef::Nil, _) = reify{ val writerStack = new WriterStack(writer.splice) }.tree
    val writerStack = c.Expr[WriterStack](Ident("writerStack"))

    val primitiveTypes =
       (typeOf[Int], (t: Tree) => reify{writerStack.splice.int(c.Expr[Int](t).splice)})::
       (typeOf[String], (t: Tree) => reify{writerStack.splice.string(c.Expr[String](t).splice)})::
       (typeOf[Float], (t: Tree) => reify{writerStack.splice.float(c.Expr[Float](t).splice)})::
       (typeOf[Double], (t: Tree) => reify{writerStack.splice.double(c.Expr[Double](t).splice)})::
       (typeOf[Boolean], (t: Tree) => reify{writerStack.splice.boolean(c.Expr[Boolean](t).splice)})::
       (typeOf[Long], (t: Tree) => reify{writerStack.splice.long(c.Expr[Long](t).splice)})::
       (typeOf[Byte], (t: Tree) => reify{writerStack.splice.byte(c.Expr[Byte](t).splice)})::
       (typeOf[BigInt], (t: Tree) => reify{writerStack.splice.bigInt(c.Expr[BigInt](t).splice)})::
       (typeOf[Short], (t: Tree) => reify{writerStack.splice.short(c.Expr[Short](t).splice)})::
       (typeOf[BigDecimal], (t: Tree) => reify{writerStack.splice.bigDecimal(c.Expr[BigDecimal](t).splice)})::
       (typeOf[Date], (t: Tree) => reify{writerStack.splice.string(defaultFormats.splice.dateFormat.format(c.Expr[Date](t).splice))})::
       (typeOf[scala.Symbol], (t: Tree) => reify{writerStack.splice.string(c.Expr[scala.Symbol](t).splice.name)})::
        Nil

    def listExpr(tpe: Type, path: Tree): Expr[Unit] = {
      val TypeRef(_, _:Symbol, pTpe::Nil) = tpe
      reify{
        writerStack.splice.startArray()
        c.Expr[scala.collection.Seq[Any]](path).splice.foreach { i =>
          c.Expr(buildTpe(pTpe, Ident("i"))).splice
        }
        writerStack.splice.endArray()
      }
    }

    def mapExpr(tpe: Type, path: Tree): Expr[Unit] = {
      val TypeRef(_, _, keyTpe::valTpe::Nil) = tpe
      if(!helpers.isPrimitive(keyTpe)) {
        c.abort(c.enclosingPosition, s"Maps needs to have keys of primitive type! Type: $keyTpe")
      }

      reify{
        writerStack.splice.startObject()
        c.Expr[scala.collection.GenMap[_, _]](path).splice.foreach { case (k, v) =>
          writerStack.splice.startField(k.toString)
          c.Expr(buildTpe(valTpe, Ident("v"))).splice
        }
        writerStack.splice.endObject()

      }
    }

    def optionExpr(tpe: Type, path: Tree): Expr[Unit] = {
      val TypeRef(_, _ :Symbol, pTpe::Nil) = tpe
      reify{
        PrimitiveHelpers.optIdent(c.Expr[Option[_]](path).splice) match {
          case Some(x) => c.Expr[Unit](buildTpe(pTpe, Ident("x"))).splice
          case None    => writerStack.splice.addJValue(org.json4s.JNothing)
        }
      }
    }

    def complexObject(oldTpe: Type, path: Tree): c.Tree = {
      val TypeRef(_, sym: Symbol, tpeArgs: List[Type]) = oldTpe.normalize
      val fields = getVars(oldTpe):::getVals(oldTpe) // get fields

      val fieldTrees = fields.flatMap { pSym =>
        val tpe = pSym.typeSignature.substituteTypes(sym.asClass.typeParams, tpeArgs)
        val fieldName = pSym.name.decoded.trim
        val fieldPath = Select(path, newTermName(fieldName))
        val startFieldExpr =  reify{writerStack.splice.startField(LIT(fieldName).splice)}
        startFieldExpr.tree::buildTpe(tpe, fieldPath)::Nil
      }

      // Return add all the blocks for each field and pop this obj off the stack
      Block(reify(writerStack.splice.startObject()).tree::fieldTrees:::
        reify{writerStack.splice.endObject()}.tree::Nil: _*)
    }

    def buildTpe(tpe: Type, path: Tree): Tree = primitiveTypes.find(_._1 =:= tpe)
      .map{ case (_, f) => f(path).tree}
      .orElse{if(tpe <:< typeOf[scala.collection.Seq[_]]) Some(listExpr(tpe, path).tree) else None }
      .orElse{if(tpe <:< typeOf[scala.collection.GenMap[_, _]]) {
          Some(mapExpr(tpe, path).tree)
        } else None}
      .orElse{if(tpe <:< typeOf[Option[_]]) Some(optionExpr(tpe, path).tree) else None }
      .getOrElse(complexObject(tpe, path))


    val tpe = weakTypeOf[U].normalize

    // Only basic types are lists maps or objects
    if (helpers.isPrimitive(tpe) || tpe =:= typeOf[Option[_]])
      c.abort(c.enclosingPosition,  s"Json4s macros cannot serialize primitive type '$tpe'")

    val tree = if(tpe <:< typeOf[scala.collection.Seq[Any]]) {
      listExpr(tpe, obj.tree).tree
    } else if(tpe <:< typeOf[scala.collection.GenMap[_, _]]) {
      mapExpr(tpe, obj.tree).tree
    } else complexObject(tpe, obj.tree)
    
    val code = Block(writerStackDef, tree)
    //println(s"------------------ Debug: Generated Code ------------------\n $code")
    c.Expr[Unit](code)
  }
}
