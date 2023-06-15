package com.srs.effort

import scala.scalajs.js.annotation.*
import org.scalajs.dom.document

@JSExportTopLevel("SrsApp")
class App {
  @JSExport
  def doSomething(containerId: String) = 
    document.getElementById(containerId).innerHTML = "YA-yo"
}
