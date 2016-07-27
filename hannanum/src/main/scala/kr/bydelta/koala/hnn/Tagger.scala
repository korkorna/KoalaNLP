package kr.bydelta.koala.hnn

import java.io.File

import kaist.cilab.jhannanum.common.communication.Sentence
import kaist.cilab.jhannanum.common.workflow.Workflow
import kaist.cilab.jhannanum.plugin.supplement.MorphemeProcessor.UnknownMorphProcessor.UnknownProcessor
import kaist.cilab.jhannanum.plugin.supplement.PlainTextProcessor.InformalSentenceFilter.InformalSentenceFilter
import kaist.cilab.jhannanum.plugin.supplement.PlainTextProcessor.SentenceSegmentor.SentenceSegmentor
import kr.bydelta.koala.Processor
import kr.bydelta.koala.data.{Morpheme, Word, Sentence => KSent}
import kr.bydelta.koala.helper.{SafeChartMorphAnalyzer, SafeHMMTagger}
import kr.bydelta.koala.traits.CanTag

import scala.collection.JavaConversions._

/**
  * 한나눔 품사분석기.
  */
final class Tagger extends CanTag[Sentence] {

  /** 한나눔 품사분석 Workflow **/
  private lazy val workflow = {
    val workflow = new Workflow
    val basePath = Dictionary.extractResource()

    workflow.appendPlainTextProcessor(new SentenceSegmentor,
      basePath + File.separator + "conf" + File.separator + "SentenceSegment.json")
    workflow.appendPlainTextProcessor(new InformalSentenceFilter,
      basePath + File.separator + "conf" + File.separator + "InformalSentenceFilter.json")

    workflow.setMorphAnalyzer(analyzer,
      basePath + File.separator + "conf" + File.separator + "ChartMorphAnalyzer.json")
    workflow.appendMorphemeProcessor(new UnknownProcessor,
      basePath + File.separator + "conf" + File.separator + "UnknownMorphProcessor.json")

    workflow.setPosTagger(new SafeHMMTagger,
      basePath + File.separator + "conf" + File.separator + "HmmPosTagger.json")
    workflow.activateWorkflow(false)
    workflow
  }
  /** 한나눔 형태소분석기 (사용자사전 개량형) **/
  private lazy val analyzer = {
    val analyzer = new SafeChartMorphAnalyzer
    analyzer.addMorphemes(Dictionary.userDict)
    analyzer
  }

  def tagSentence(text: String): KSent = {
    convert(tagSentenceRaw(text))
  }

  override def tagSentenceRaw(text: String): Sentence = {
    workflow.analyze(text)
    workflow.getResultOfSentence(new Sentence(0, 0, false))
  }

  def tagParagraph(text: String): Seq[KSent] = {
    workflow.analyze(text)
    workflow.getResultOfDocument(new Sentence(0, 0, false)).map(convert)
  }

  override private[koala] def convert(result: Sentence): KSent =
    new KSent(
      words =
        result.getEojeols.zip(result.getPlainEojeols).map {
          case (eojeol, plain) =>
            new Word(
              surface = plain,
              morphemes =
                eojeol.getMorphemes.zip(eojeol.getTags).map {
                  case (morph, tag) =>
                    new Morpheme(surface = morph, rawTag = tag, processor = Processor.Hannanum)
                }
            )
        }
    )

  @throws[Throwable]
  override protected def finalize() {
    workflow.close()
    super.finalize()
  }
}