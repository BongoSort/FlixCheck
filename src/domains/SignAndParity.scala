package domains

import impl.logic._
import syntax.Symbols._

object SignAndParity {

  val Elements = Type.Constructor2("SP", Sign.Elements, Parity.Elements)

  val LeqSymbol = Symbol.PredicateSymbol("SP.Leq")
  val JoinSymbol = Symbol.PredicateSymbol("SP.Join")
  val SumSymbol = Symbol.PredicateSymbol("SP.Sum")

  val Bot = Term.Constant(Value.Constructor2("SP", Sign.Bot.v, Parity.Bot.v))

  val Leq = Set(
    HornClause(
      head = Predicate(LeqSymbol, List(
        Term.Constructor2("SP", Term.Variable("s1"), Term.Variable("p1")),
        Term.Constructor2("SP", Term.Variable("s2"), Term.Variable("p2"))
      )),
      body = Set(
        Predicate("Sign.Leq", List(Term.Variable("s1"), Term.Variable("s2"))),
        Predicate("Parity.Leq", List(Term.Variable("p1"), Term.Variable("p2")))
      ))
  )

  val Join = Set(
    HornClause(
      head = Predicate(JoinSymbol, List(
        Term.Constructor2("SP", Term.Variable("s1"), Term.Variable("p1")),
        Term.Constructor2("SP", Term.Variable("s2"), Term.Variable("p2")),
        Term.Constructor2("SP", Term.Variable("s3"), Term.Variable("p3"))
      )),
      body = Set(
        Predicate("Sign.Join", List(Term.Variable("s1"), Term.Variable("s2"), Term.Variable("s3"))),
        Predicate("Parity.Join", List(Term.Variable("p1"), Term.Variable("p2"), Term.Variable("p3")))
      )))

  val Sum = Set(
    HornClause(
      head = Predicate(SumSymbol, List(
        Term.Constructor2("SP", Term.Variable("s1"), Term.Variable("p1")),
        Term.Constructor2("SP", Term.Variable("s2"), Term.Variable("p2")),
        Term.Constructor2("SP", Term.Variable("s3"), Term.Variable("p3"))
      )),
      body = Set(
        Predicate("Sign.Sum", List(Term.Variable("s1"), Term.Variable("s2"), Term.Variable("s3"))),
        Predicate("Parity.Sum", List(Term.Variable("p1"), Term.Variable("p2"), Term.Variable("p3")))
      )
    )
  )

  val Interpretations = Map(
    LeqSymbol -> Interpretation.Relation.In2,
    JoinSymbol -> Interpretation.Relation.In3,
    SumSymbol -> Interpretation.Relation.In3
  )

  val lattice = Lattice(Elements, Bot.v, LeqSymbol, JoinSymbol, Leq ++ Join ++ Sum, Interpretations)

}
