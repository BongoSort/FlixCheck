package impl.solver

import impl.logic.Symbol.{NamedSymbol => NSym, PredicateSymbol => PSym, VariableSymbol => VSym}
import impl.logic._
import util.collection.mutable

/**
 * A semi-naive solver.
 */
class Solver(program: Program) {

  /**
   * Relations.
   */
  val relation1 = mutable.MultiMap1.empty[PSym, Value]
  val relation2 = mutable.MultiMap2.empty[PSym, Value, Value]
  val relation3 = mutable.MultiMap3.empty[PSym, Value, Value, Value]
  val relation4 = mutable.MultiMap4.empty[PSym, Value, Value, Value, Value]
  val relation5 = mutable.MultiMap5.empty[PSym, Value, Value, Value, Value, Value]

  /**
   * Lattice Maps.
   */
  val map1 = mutable.Map1.empty[PSym, Value]
  val map2 = mutable.Map2.empty[PSym, Value, Value]
  val map3 = mutable.Map3.empty[PSym, Value, Value, Value]
  val map4 = mutable.Map4.empty[PSym, Value, Value, Value, Value]
  val map5 = mutable.Map5.empty[PSym, Value, Value, Value, Value, Value]

  /**
   * Dependencies.
   */
  val dependencies = mutable.MultiMap1.empty[PSym, HornClause]

  /**
   * Worklist.
   */
  val queue = scala.collection.mutable.Queue.empty[(HornClause, Map[VSym, Value])]

  /**
   * Fixpoint computation.
   */
  def solve(): Unit = {
    // Find dependencies between predicates and horn clauses.
    // A horn clause `h` depends on predicate `p` iff `p` occurs in the body of `h`.
    for (h <- program.clauses; p <- h.body) {
      dependencies.put(p.name, h)
    }

    // Satisfy all facts. Satisfying a fact adds violated horn clauses to the work list.
    for (h <- program.facts) {
      satisfy(h.head, program.interpretation, Map.empty[VSym, Value])
    }

    // Iteratively try to resolve horn clauses.
    // Resolving a horn clause may add new facts and thus new items to the work list.
    while (queue.nonEmpty) {
      val (h, env) = queue.dequeue()
      resolve(h, program.interpretation, env)
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Resolution                                                              //
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Try to satisfy
   *
   * Adds all facts which satisfies the given horn clause `h`.
   */
  def resolve(h: HornClause, inv: Map[PSym, Interpretation], env: Map[VSym, Value]): Unit = evaluate(h, inv, env) match {
    case Model.Unsat => // nop
    case Model.Sat(envs) =>
      for (env <- envs) {
        satisfy(h.head, inv, env)
      }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Evaluation                                                              //
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a satisfiable model of the given horn clause `h` with interpretations `inv` under the given environment `env`.
   */
  def evaluate(h: HornClause, inv: Map[PSym, Interpretation], env: Map[VSym, Value]): Model = {
    val relationals = h.body filter (p => isRelational(p, inv))
    val functionals = h.body -- relationals

    val init = Model.Sat(env)
    val predicates = relationals.toList ::: functionals.toList

    (init /: predicates) {
      case (m, p) => evaluate(p, interpretationOf(p, inv), m)
    }
  }

  /**
   *
   */
  def evaluate(p: Predicate, i: Interpretation, m: Model): Model = m match {
    case Model.Unsat => Model.Unsat
    case Model.Sat(envs) => {

      val models = envs map (env => evaluate(p, i, env))

      val xs = models.map({
        case Model.Unsat => Set.empty[Map[VSym, Value]]
        case Model.Sat(env) => env // TODO: Call evaluate
      }).flatten

      Model.Sat(xs)
    }
  }


  /**
   * Returns a model for the given predicate `p` with interpretation `i` under the given environment `env`.
   */
  def evaluate(p: Predicate, i: Interpretation, env: Map[VSym, Value]): Model = i match {
    case Interpretation.Proposition(Value.Bool(true)) => Model.Sat(env)
    case Interpretation.Proposition(Value.Bool(false)) => Model.Unsat
    case Interpretation.Relation.In1(t1) =>
      lookupTerm(p, 0) match {
        case Term.Constant(v) => if (relation1.has(p.name, v)) Model.Sat(env) else Model.Unsat
        case Term.Variable(s) => Model.Sat(relation1.get(p.name) map (v => env + (s -> v)))
        case t =>
          for (v <- relation1.get(p.name)) {
            val env2 = unify(t, v)
          }
          ???
      }
    case Interpretation.Relation.In2(t1, t2) =>
      (lookupTerm(p, 0), lookupTerm(p, 1)) match {
        case (Term.Constant(v1), Term.Constant(v2)) => ???
        case (Term.Variable(var1), Term.Variable(var2)) =>
          // TODO: Must take bindings into account.. use substitute...
          val r = scala.collection.mutable.Set.empty[Map[VSym, Value]]
          for ((v1, v2) <- relation2.get(p.name).tuples) {

          }
          ???
        case _ => throw new RuntimeException(i.toString)
      }
    case _ => throw new RuntimeException(i.toString)
  }


  /////////////////////////////////////////////////////////////////////////////
  // Satisfy                                                                 //
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Satisfies the given predicate `p` under the given interpretations `inv` and environment `env`.
   */
  def satisfy(p: Predicate, inv: Map[PSym, Interpretation], env: Map[VSym, Value]): Unit =
    satisfy(p, interpretationOf(p, inv), env)

  /**
   * Satisfies the given predicate `p` under the given interpretation `i` and environment `env`.
   */
  def satisfy(p: Predicate, i: Interpretation, env: Map[VSym, Value]): Unit = i match {
    case Interpretation.Relation.In1(t1) =>
      val v = lookupValue(p, 0, env)
      val newFact = relation1.put(p.name, v)
      if (newFact)
        propagate(p, IndexedSeq(v))

    case Interpretation.Relation.In2(t1, t2) =>
      val k1 = lookupValue(p, 0, env)
      val v = lookupValue(p, 1, env)
      val newFact = relation2.put(p.name, k1, v)
      if (newFact)
        propagate(p, IndexedSeq(k1, v))

    case Interpretation.Relation.In3(t1, t2, t3) =>
      val k1 = lookupValue(p, 0, env)
      val k2 = lookupValue(p, 1, env)
      val v = lookupValue(p, 2, env)
      val newFact = relation3.put(p.name, k1, k2, v)
      if (newFact)
        propagate(p, IndexedSeq(k1, k2, v))

    case Interpretation.Relation.In4(t1, t2, t3, t4) =>
      val k1 = lookupValue(p, 0, env)
      val k2 = lookupValue(p, 1, env)
      val k3 = lookupValue(p, 2, env)
      val v = lookupValue(p, 3, env)
      val newFact = relation4.put(p.name, k1, k2, k3, v)
      if (newFact)
        propagate(p, IndexedSeq(k1, k2, k3, v))

    case Interpretation.Relation.In5(t1, t2, t3, t4, t5) =>
      val k1 = lookupValue(p, 0, env)
      val k2 = lookupValue(p, 1, env)
      val k3 = lookupValue(p, 2, env)
      val k4 = lookupValue(p, 3, env)
      val v = lookupValue(p, 4, env)
      val newFact = relation5.put(p.name, k1, k2, k3, k4, v)
      if (newFact)
        propagate(p, IndexedSeq(k1, k2, k3, k4, v))

    case Interpretation.Map.Leq1(t1) => ???
    case Interpretation.Map.Leq2(t1, t2) => ???
    case Interpretation.Map.Leq3(t1, t2, t3) => ???
    case Interpretation.Map.Leq4(t1, t2, t3, t4) => ???
    case Interpretation.Map.Leq5(t1, t2, t3, t4, t5) => ???

    case _ => throw new Error.NonRelationalPredicate(p)
  }


  /**
   * Enqueues all depedencies of the given predicate with the given environment.
   */
  def propagate(p: Predicate, env: IndexedSeq[Value]): Unit = {
    for (h <- dependencies.get(p.name)) {
      queue.enqueue((h, bind(h, p, env)))
    }
  }

  /**
   * Returns a new environment where all free variables, for the given predicate `p`,
   * have been mapped to the value in the given environment `env`.
   *
   * That is, if the horn clause is A(x, y, z) :- B(x, y), C(z), the predicate is B
   * and the environment is [0 -> a, 1 -> b] then the return environment is [x -> a, y -> b].
   */
  def bind(h: HornClause, p: Predicate, env: IndexedSeq[Value]): Map[VSym, Value] = {
    val m = scala.collection.mutable.Map.empty[VSym, Value]
    for (p2 <- h.body; if p.name == p2.name) {
      for ((t, i) <- p2.terms.zipWithIndex) {
        t match {
          case Term.Variable(s) => m += (s -> env(i))
          case _ => // nop
        }
      }
    }
    m.toMap
  }


  /////////////////////////////////////////////////////////////////////////////
  // Models                                                                  //
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Model

  object Model {

    case object Unsat extends Model

    object Sat {
      def apply(env: Map[VSym, Value]): Model = Sat(Set(env))
    }

    case class Sat(envs: Set[Map[VSym, Value]]) extends Model

    // TODO: Sooo..what about the empty set of envs?

  }

  /////////////////////////////////////////////////////////////////////////////
  // Utilities                                                               //
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Returns the term of the variable with the given `index` in the given predicate `p`.
   */
  def lookupTerm(p: Predicate, index: Int): Term = p.terms.lift(index) match {
    case None => throw new Error.PredicateArityMismatch(p, index)
    case Some(t) => t
  }

  /**
   * Returns the value of the variable with the given `index` in the given predicate `p`.
   */
  def lookupValue(p: Predicate, index: Int, env: Map[VSym, Value]): Value = p.terms.lift(index) match {
    case None => throw new Error.PredicateArityMismatch(p, index)
    case Some(t) => substituteValue(t, env)
  }

  /**
   * Either returns a fully evaluated value or a term with variables replaced by their corresponding values from the given environment `env`.
   */
  def substitute(t: Term, env: Map[VSym, Value]): Either[Term, Value] = t.asValue(env) match {
    case None => Left(substituteTerm(t, env))
    case Some(v) => Right(v)
  }

  /**
   * Returns the term obtained from `t` by replacing all free variables in `t` with their corresponding values from the given environment `env`.
   */
  def substituteTerm(t: Term, env: Map[VSym, Value]): Term = t match {
    case Term.Constant(v) => Term.Constant(v)
    case Term.Variable(s) => env.get(s) match {
      case None => Term.Variable(s)
      case Some(v) => Term.Constant(v)
    }
    case Term.Constructor0(s) => Term.Constructor0(s)
    case Term.Constructor1(s, t1) => Term.Constructor1(s, substituteTerm(t1, env))
    case Term.Constructor2(s, t1, t2) => Term.Constructor2(s, substituteTerm(t1, env), substituteTerm(t2, env))
    case Term.Constructor3(s, t1, t2, t3) => Term.Constructor3(s, substituteTerm(t1, env), substituteTerm(t2, env), substituteTerm(t3, env))
    case Term.Constructor4(s, t1, t2, t3, t4) => Term.Constructor4(s, substituteTerm(t1, env), substituteTerm(t2, env), substituteTerm(t3, env), substituteTerm(t4, env))
    case Term.Constructor5(s, t1, t2, t3, t4, t5) => Term.Constructor5(s, substituteTerm(t1, env), substituteTerm(t2, env), substituteTerm(t3, env), substituteTerm(t4, env), substituteTerm(t5, env))
  }

  /**
   * Returns the value obtained from `t` by replacing all free variables in `t` with their corresponding values from the given environment `env`.
   *
   * Throws an exception if a variable is unbound.
   */
  def substituteValue(t: Term, env: Map[VSym, Value]): Value = t match {
    case Term.Constant(v) => v
    case Term.Variable(s) => env.getOrElse(s, throw new Error.UnboundVariable(s))
    case Term.Constructor0(s) => Value.Constructor0(s)
    case Term.Constructor1(s, t1) => Value.Constructor1(s, substituteValue(t1, env))
    case Term.Constructor2(s, t1, t2) => Value.Constructor2(s, substituteValue(t1, env), substituteValue(t2, env))
    case Term.Constructor3(s, t1, t2, t3) => Value.Constructor3(s, substituteValue(t1, env), substituteValue(t2, env), substituteValue(t3, env))
    case Term.Constructor4(s, t1, t2, t3, t4) => Value.Constructor4(s, substituteValue(t1, env), substituteValue(t2, env), substituteValue(t3, env), substituteValue(t4, env))
    case Term.Constructor5(s, t1, t2, t3, t4, t5) => Value.Constructor5(s, substituteValue(t1, env), substituteValue(t2, env), substituteValue(t3, env), substituteValue(t4, env), substituteValue(t5, env))
  }

  /**
   * Returns an environment obtained by unifying the given term `t` with the value `v`.
   *
   * The resulting environment contains an entry `x` -> `y` iff `t` contains a free variable `x` where `v` contains a value `y`.
   *
   * TODO: Careful about existing bindings/free variables occuring in multiple places.
   */
  def unify(t: Term, v: Value): Map[VSym, Value] = (t, v) match {
    case (Term.Constant(v1), v2) if v1 == v2 => Map.empty
    case (Term.Variable(s), v2) => Map(s -> v2)
    case (Term.Constructor0(s1), Value.Constructor0(s2)) if s1 == s2 => Map.empty
    case (Term.Constructor1(s1, t1), Value.Constructor1(s2, v1)) if s1 == s2 => unify(t1, v1)
    case (Term.Constructor2(s1, t1, t2), Value.Constructor2(s2, v1, v2)) if s1 == s2 => unify(t1, v1) ++ unify(t2, v2)
    case (Term.Constructor3(s1, t1, t2, t3), Value.Constructor3(s2, v1, v2, v3)) if s1 == s2 => unify(t1, v1) ++ unify(t2, v2) ++ unify(t3, v3)
    case (Term.Constructor4(s1, t1, t2, t3, t4), Value.Constructor4(s2, v1, v2, v3, v4)) if s1 == s2 => unify(t1, v1) ++ unify(t2, v2) ++ unify(t3, v3) ++ unify(t4, v4)
    case (Term.Constructor5(s1, t1, t2, t3, t4, t5), Value.Constructor5(s2, v1, v2, v3, v4, v5)) if s1 == s2 => unify(t1, v1) ++ unify(t2, v2) ++ unify(t3, v3) ++ unify(t4, v4) ++ unify(t5, v5)
    case _ => throw new Error.UnificationError(t, v)
  }

  /**
   * Returns a new environment obtained by extending the environment `env1` with `env2`.
   * If a key is present in both `env1` and `env2` the value from `env2` is used.
   */
  def extend(env1: Map[VSym, Value], env2: Map[VSym, Value]): Map[VSym, Value] = (env1 /: env2) {
    case (acc, (s, v)) => acc + (s -> v)
  }

  /**
   * Returns the interpretation of the given predicate `p`.
   */
  def interpretationOf(p: Predicate, inv: Map[PSym, Interpretation]): Interpretation = inv.get(p.name) match {
    case None => throw new Error.InterpretationNotFound(p.name)
    case Some(i) => i
  }

  /**
   * Returns `true` iff the given predicate `p` is relational under the given interpretations `inv`.
   */
  private def isRelational(p: Predicate, inv: Map[PSym, Interpretation]): Boolean = interpretationOf(p, inv) match {
    case _: Interpretation.Relation.In1 => true
    case _: Interpretation.Relation.In2 => true
    case _: Interpretation.Relation.In3 => true
    case _: Interpretation.Relation.In4 => true
    case _: Interpretation.Relation.In5 => true
    case _: Interpretation.Map.Leq1 => true
    case _: Interpretation.Map.Leq2 => true
    case _: Interpretation.Map.Leq3 => true
    case _: Interpretation.Map.Leq4 => true
    case _: Interpretation.Map.Leq5 => true
    case _ => false
  }
}
