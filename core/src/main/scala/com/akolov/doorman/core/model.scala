package com.akolov.doorman.core

trait DoormanError
case class ConfigurationNotFound(name: String) extends DoormanError
case class ConfigurationError(desc: String) extends DoormanError
