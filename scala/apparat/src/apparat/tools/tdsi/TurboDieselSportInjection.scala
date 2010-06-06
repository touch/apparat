/*
 * This file is part of Apparat.
 *
 * Apparat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Apparat is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Apparat. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2009 Joa Ebert
 * http://www.joa-ebert.com/
 *
 */
package apparat.tools.tdsi

import apparat.tools.{ApparatConfiguration, ApparatApplication, ApparatTool}
import java.io.{File => JFile}
import apparat.utils.TagContainer
import apparat.actors.Futures._
import apparat.abc._
import apparat.bytecode.operations._
import apparat.bytecode.combinator._
import apparat.bytecode.combinator.BytecodeChains._
import apparat.swf._
import apparat.bytecode.optimization.{InlineExpansion, MacroExpansion, PeepholeOptimizations, InlineMemory}

/**
 * @author Joa Ebert
 */
object TurboDieselSportInjection {
	def main(args: Array[String]): Unit = ApparatApplication(new TDSITool, args)

	class TDSITool extends ApparatTool
	{
		var input: JFile = _
		var output: JFile = _
		var alchemy = true
		var macros = true
		var inline = true

		override def name = "Turbo Diesel Sport Injection"

		override def help = """  -i [file]			Input file
  -o [file]			Output file (optional)
  -a (true|false)	Inline Alchemy operations
  -e (true|false)	Inline expansion
  -m (true|false)	Macro expansion"""

		override def configure(config: ApparatConfiguration): Unit = configure(TDSIConfigurationFactory fromConfiguration config)

		def configure(config: TDSIConfiguration): Unit = {
			input = config.input
			output = config.output
			alchemy = config.alchemyExpansion
			macros = config.macroExpansion
			inline = config.inlineExpansion
		}

		override def run() = {
			SwfTags.tagFactory = (kind: Int) => kind match {
				case SwfTags.DoABC => Some(new DoABC)
				case SwfTags.DoABC1 => Some(new DoABC)
				case _ => None
			}

			val source = input
			val target = output
			val cont = TagContainer fromFile source
			val allABC = Map((for(doABC <- cont.tags collect { case doABC: DoABC => doABC }) yield (doABC -> (Abc fromDoABC doABC))):_*)
			val macroExpansion = if(macros) Some(new MacroExpansion(allABC.valuesIterator.toList)) else None
			val inlineExpansion = if(inline) Some(new InlineExpansion(allABC.valuesIterator.toList)) else None

			for((doABC, abc) <- allABC.iterator) {
				abc.loadBytecode()

				for(method <- abc.methods) {
					method.body match {
						case Some(body) => {
							body.bytecode match {
								case Some(bytecode) => {
									//TODO to do this right, we need to repeat this steps
									//until the code keeps changing!
									if(inline) {
										inlineExpansion.get expand bytecode
									}

									if(macros) {
										macroExpansion.get expand bytecode
									}

									if(alchemy) {
										InlineMemory(bytecode)
									}

									PeepholeOptimizations(bytecode)
								}
								case None =>
							}
						}
						case None =>
					}
				}

				abc.saveBytecode()
				abc write doABC
			}

			cont write target
		}
	}
}