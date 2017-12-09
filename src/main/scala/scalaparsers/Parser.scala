package scalaparsers

import scalaparsers.Document.text

import scala.collection.immutable.List

import cats.kernel.Comparison.{EqualTo, GreaterThan, LessThan}
import cats.free.{Free, Trampoline}
import cats.implicits._

/** A parser with a nice error handling
  *
  * @author EAK
  */
abstract class Parser[S, +A] extends MonadicPlus[Parser[S, +?], A] { that =>
  def self = that
  def apply[B >: A](s: ParseState[S], vs: Supply): Trampoline[ParseResult[S,B]]
  def run(s: ParseState[S], vs: Supply): Either[Err, (ParseState[S], A)] = apply(s,vs).run match {
    case Pure(a,_)      => Right((s,a))
    case Commit(t,a,_)  => Right((t,a))
    case Fail(b,aux,xs) => Left(Err.report(s.loc,b,aux,xs))
    case e: Err         => Left(e)
  }

  /** Functor */
  def map[B](f: A => B) = new Parser[S,B] {
    def apply[C >: B](s: ParseState[S], vs: Supply) = that(s, vs).map(_ map f)
  }

  // filtered
  def lift[B](p: Parser[S,B]) = p
  def withFilter(p : A => Boolean): Parser[S,A] = new Parser[S,A] {
    def apply[B >: A](s: ParseState[S], vs: Supply) = that(s, vs).map {
      case Pure(a,e) if !p(a) => e
      case Commit(t,a,xs) if !p(a) => Err.report(t.loc,None,List(),xs)
      case r => r
    }
  }
  override def filterMap[B](f: A => Option[B]) = new Parser[S,B] {
    def apply[C >: B](s: ParseState[S], vs: Supply) = that(s, vs).map {
      case Pure(a,e) => f(a) match {
        case Some(b) => Pure(b, e)
        case None => e
      }
      case Commit(s,a,xs) => f(a) match {
        case Some(b) => Commit(s,b,xs)
        case None    => Err.report(s.loc, None, List())
      }
      case r : ParseFailure => r
    }
  }

  /** Monad */
  def flatMap[B](f: A => Parser[S,B]) = new Parser[S,B] {
    def apply[C >: B](s: ParseState[S], vs: Supply) = that(s, vs).flatMap {
      case r@Pure(a, e)  => f(a)(s, vs).map {
        case Pure(b, ep) => Pure(b, e ++ ep)
        case r : Fail => e ++ r
        case r        => r
      }
      case Commit(t, a, xs) => f(a)(t, vs).map {
        case Pure(b, Fail(_, _, ys)) => Commit(t, b, xs ++ ys)
        case Fail(e, aux, ys) => Err.report(t.loc, e, aux, xs ++ ys)
        case r => r
      }
      case r : ParseFailure => Free.pure(r)
    }
  }

  def wouldSucceed: Parser[S, Boolean] = new Parser[S,Boolean] {
    def apply[B >: Boolean](s: ParseState[S], vs: Supply) = that(s, vs).map {
      case e : ParseFailure => Pure(false)
      case _                => Pure(true)
    }
  }

  def race[B >: A](p: Parser[S, B]) = new Parser[S,B] {
    def apply[C >: B](s: ParseState[S], vs: Supply) = that(s, vs).flatMap {
      case e : Fail => p(s, vs) map {
        case ep : Fail => e ++ ep
        case Pure(b, ep) => Pure[B](b, e ++ ep)
        case r => r
      }
      case e@Err(l,msg,aux,stk) => p(s, vs) map {
        case _ : Fail => e
        case ep@Err(lp,msgp,auxp,stkp) => (l comparison ep.loc) match {
          case LessThan    => ep
          case EqualTo     => e
          case GreaterThan => e
        }
        case r => r
      }
      case r => Free.pure(r)
    }
  }

  /** MonadPlus */
  def |[B >: A](other: => Parser[S,B]) = new Parser[S,B] {

    def apply[C >: B](s: ParseState[S], vs: Supply) = that(s, vs).flatMap {
      case e : Fail => other(s, vs).map {
        case ep : Fail => e ++ ep
        case Pure(a, ep) => Pure(a, e ++ ep)
        case r => r
      }
      case r => Free.pure(r)
    }
  }
  def orElse[B >: A](b: => B) = new Parser[S,B] {
    def apply[C >: B](s: ParseState[S], vs: Supply) = that(s, vs).map {
      case e : Fail => Pure(b, e)
      case r => r
    }
  }

  // context
  def scope(desc: String) = new Parser[S,A] {
    def apply[B >: A](s: ParseState[S], vs: Supply) = that(s, vs).map {
      case Fail(m, aux, _)                     => Fail(m, aux, Set(desc))
      case Err(p,d,aux,stk) if s.tracing       => Err(p,d,aux,(s.loc,desc)::stk)
      case Pure(a, Fail(m : Some[Document], aux, _)) => Pure(a, Fail(m, aux, Set(desc))) // TODO: can we drop the Some?
      case r => r
    }
  }

  /** Allow backtracking to retry after a parser state change */
  def attempt = new Parser[S,A] {
    def apply[B >: A](s: ParseState[S], vs: Supply) = that(s, vs).map {
      case e@Err(p,d,aux,stk) => Fail(None, List(e.pretty), Set()) // we can attach the current message, now!
      case r       => r
    }
  }
  def attempt(s: String): Parser[S,A] = attempt scope s

  def not = new Parser[S,Unit] {
    def apply[X >: Unit](s: ParseState[S], vs: Supply) = that(s, vs).map {
      case Pure(a, _) => Fail(Some("unexpected" :+: text(a.toString)))
      case Commit(t, a, _)  => Err.report(s.loc, Some("unexpected" :+: text(a.toString)), List(), Set())
      case _                => Pure[Unit](())
    }
  }
  def handle[B >: A](f: ParseFailure => Parser[S,B]) = new Parser[S,B] {
    def apply[C >: B](s: ParseState[S], vs: Supply) = that(s, vs).flatMap {
      case r : Err        => f(r)(s, vs)
      case r@Fail(e, aux, xs)   => f(r)(s, vs).map {
        case Fail(ep, auxp, ys)  => Fail(ep orElse e, if (ep.isDefined) auxp else aux, xs ++ ys)
        case r => r
      }
      case r => Free.pure(r)
    }
  }
  def slice = new Parser[S,String] {
    def apply[X >: String](s: ParseState[S], vs: Supply) = that(s, vs).map {
      case Pure(_, e)       => Pure("", e)
      case Commit(t, _, xs) => Commit(t, s.input.substring(s.offset, t.offset), xs)
         // s.rest.take(s.rest.length - t.rest.length), xs)
      case r : ParseFailure => r
    }
  }
  def when(b: Boolean): Parser[S,Unit] = if (b) skip else Parser( (x:ParseState[S], y:Supply) => Pure[Unit](()))
}

object Parser {
  def apply[A,S](f: (ParseState[S], Supply) => ParseResult[S,A]) = new Parser[S,A] {
    def apply[B >: A](s: ParseState[S], vs: Supply) = Free.pure(f(s, vs))
  }
}
