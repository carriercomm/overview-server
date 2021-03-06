package org.overviewproject.query

import scala.util.parsing.combinator.RegexParsers

object QueryParser {
  def parse(input: String): Either[SyntaxError,Query] = {
    Grammar.parse(Grammar.phrase(Grammar.expression), input) match {
      case Grammar.Success(node, _) => Right(node)
      case Grammar.Failure(msg, next) => Left(SyntaxError(msg, next.offset))
      case Grammar.Error(msg, next) => Left(SyntaxError(msg, next.offset))
    }
  }

  private object Grammar extends RegexParsers {
    /*
     * These regexes limit length so that String.toInt can't throw. (There may
     * be other valid reasons for limiting length, but those aren't considered
     * here.)
     *
     * If the regexes fail, we fall back to a PhraseQuery.
     */
    private val FuzzyTermWithFuzz = """([^ ]*)~(\d{1,7})""".r
    private val FuzzyTermWithoutFuzz = """([^ ]*)~""".r
    private val ProximityPhrase = """(.*)~(\d{1,7})""".r // only makes sense after FuzzyTerm* fail

    private def removeBackslashes(s: String) = s.replaceAll("\\\\(.)", "$1")
    private def stringToNode(s: String): Query = s match {
      case FuzzyTermWithFuzz(term, fuzzString) => FuzzyTermQuery(term, Some(fuzzString.toInt))
      case FuzzyTermWithoutFuzz(term) => FuzzyTermQuery(term, None)
      case ProximityPhrase(phrase, slopString) => ProximityQuery(phrase, slopString.toInt)
      case _ => PhraseQuery(s)
    }

    def expression: Parser[Query] = chainl1(unaryExpression, binaryOperator)
    def unaryExpression: Parser[Query] = parensExpression | notExpression | term

    def term: Parser[Query] = (quotedString | unquotedString) ^^ stringToNode

    def quotedString: Parser[String]
      = (singleQuotedString | doubleQuotedString | smartQuotedString) ~ regex("""~(\d{1,7})""".r).? ^^
      { n => n._1 + n._2.getOrElse("") }

    def singleQuotedString: Parser[String]
      = "'" ~> regex("""((\\.)|[^'])*""".r) <~ "'" ^^
      { s => removeBackslashes(s) }

    def doubleQuotedString: Parser[String]
      = "\"" ~> regex("""((\\.)|[^"])*""".r) <~ "\"" ^^
      { s => removeBackslashes(s) }

    def smartQuotedString: Parser[String]
      = "“" ~> regex("""((\\.)|[^”])*""".r) <~ "”" ^^
      { s => removeBackslashes(s) }

    def unquotedString: Parser[String]
      = rep1(unquotedWord) ^^ { _.mkString(" ") }

    def unquotedWord: Parser[String]
      = guard(not(operator)) ~> regex("""[^ ()]+""".r)

    def operator = andOperator | orOperator | notOperator
    def andOperator = regex("(?i)and".r) ^^^ (())
    def orOperator = regex("(?i)or".r) ^^^ (())
    def notOperator = regex("(?i)not".r) ^^^ (())

    def parensExpression: Parser[Query] = "(" ~> expression <~ ")"
    def notExpression: Parser[NotQuery] = notOperator ~> unaryExpression ^^ NotQuery.apply _
    def binaryOperator: Parser[(Query, Query) => Query]
      = (andOperator ^^^ AndQuery.apply _) | (orOperator ^^^ OrQuery.apply _)
  }
}
