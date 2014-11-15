package tql


/**
 * Created by Eric on 20.10.2014.
 */

trait Combinators[T] { self: Traverser[T] =>

  import scala.reflect.ClassTag
  import scala.collection.generic.CanBuildFrom
  import scala.language.experimental.macros

  /**
   * Traverse the children of the tree
   * */
  def children[A : Monoid](f: Matcher[A]) =  Matcher[A]{ tree =>
    traverse(tree, f)
  }

  /**
   * Traverse the tree in a TopDown manner, stop when a transformation/traversal has succeeded
   * */
  def downBreak[A : Monoid](m: Matcher[A]): Matcher[A] =
    m | children(downBreak[A](m))

  /**
   * Traverse the tree in a BottomUp manner, stop when a transformation/traversal has succeeded
   * */
  def upBreak[A : Monoid](m: Matcher[A]): Matcher[A] =
    children(upBreak[A](m)) | m

  /**
   * Same as TopDown, but does not sop when a transformation/traversal has succeeded
   * */
  /*def down[A : Monoid](m: Matcher[A]): Matcher[A] =
    m + children(down[A](m))*/

  def down: {
    def apply[A : Monoid](m: Matcher[A]): Matcher[A]
    def $ [A : Monoid](m: Matcher[A]): Matcher[A]
  } = new {
    def apply[A : Monoid](m: Matcher[A]): Matcher[A] = m + children(down[A](m))

    def $ [A : Monoid](m: Matcher[A]): Matcher[A] = apply(m)
  }


  /**
   * Same as upBreak, but does not sop when a transformation/traversal has succeeded
   * */
  def up[A : Monoid](m: Matcher[A]): Matcher[A] =
    children(up[A](m)) + m

  /**
   * Succeed if the partial function f applied on the tree is defined and return true
   * */
  def guard[U <: T : ClassTag](f: PartialFunction[U, Boolean]) = Matcher[U]{
    case t: U if f.isDefinedAt(t) && f(t) => Some((t, t))
    case _ => None
  }

  /**
   *  Transform a I into a T where both I and O are subtypes of T and where a transformation from I to O is authorized
   * */
  def transform[I <: T : ClassTag, O <: T](f: PartialFunction[I, O])(implicit x: AllowedTransformation[I, O]) =
    Matcher[Unit] {
      case t: I if f.isDefinedAt(t) => Some((f(t), Monoid.Void.zero))
      case _ => None
    }

  def flatMap[B](f: T => MatcherResult[B]) = Matcher[B] {tree =>
    f(tree)
  }

  def visit[A](f: PartialFunction[T, A])(implicit x: ClassTag[T]) =
    guard[T]{case t => f.isDefinedAt(t)} map(f(_))

  case class Maybe[T, +A](a: Option[T], b: Option[A])

  implicit def TToMaybe(t: T): Maybe[T, Nothing] = Maybe(Some(t), None)
  implicit class TMaybeEnhencer(t: T){
    def get[A](a: A) = Maybe(Some(t), Some(a))
  }
  implicit class ResultMaybeEnhencer[A](a: A){
    def get = Maybe[T, A](None, Some(a))
  }

  object get {
    def apply[A](a: A) = Maybe[T, A](None, Some(a))
  }

  def visit2[A : Monoid](f: PartialFunction[T, Maybe[T, A]]) = Matcher[A]{
    case tree if f.isDefinedAt(tree) => f(tree) match {
      case Maybe(Some(a), Some(b)) => Some((a, b))
      case Maybe(Some(a), None) => Some((a, implicitly[Monoid[A]].zero))
      case Maybe(None, Some(b)) => Some((tree, b))
      case _ => None
    }
    case _ => None
  }


  def stateful[A, B](init: => A)(f: ( => A) => Matcher[(B, A)]): Matcher[B] = {
    var state = init
    f(state) map {case (res, s) =>
      state = s
      res
    }
  }

  /**
   * Same as filter but puts the results into a list
   * */
  def collect[A : ClassTag](f: PartialFunction[T, A])(implicit x: ClassTag[T]): Matcher[List[A]] =
    guard[T]{case t => f.isDefinedAt(t)} map (x => List(f(x)))

  def collectIn[C[_]] = new {
    def apply[A](f: PartialFunction[T, A])(implicit  x: ClassTag[T], y: CanBuildFrom[C[A], A, C[A]]) =
      guard[T]{case t => f.isDefinedAt(t)} map(t => (y() += f(t)).result)
  }

  def collectIn2[V[_, _]] = new {
    def apply[A, B](f: PartialFunction[T, (A, B)])(implicit  x: ClassTag[T], y: CanBuildFrom[V[A, B], (A, B), V[A, B]]) =
      guard[T]{case t => f.isDefinedAt(t)} map(t => (y() += f(t)).result)
  }

  /**
   * Syntactic sugar for guard combinator so that one doesn't need to type the type parameter
   * */
  def filter(f: PartialFunction[T, Boolean]): Matcher[T] = macro CombinatorsSugar.filterSugarImpl[T]

  /**
  * Syntactic sugar for transform combinator so that one doesn't need to type the type parameter
    * */
  def update(f: PartialFunction[T, T]): Matcher[Unit] = macro CombinatorsSugar.updateSugarImpl[T]

}