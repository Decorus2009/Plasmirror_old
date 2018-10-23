package ui.controllers


import MainApp
import core.ComputationParametersStorage
import core.Polarization
import core.State
import core.validators.MultipleExportDialogParametersValidator
import core.validators.ValidationResult.SUCCESS
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCode.*
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination.SHIFT_DOWN
import javafx.scene.input.KeyCombination.SHORTCUT_DOWN
import javafx.scene.input.KeyEvent
import javafx.scene.layout.AnchorPane
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.File.separator
import java.nio.file.Paths


class MenuController {

    lateinit var rootController: RootController

    @FXML
    private lateinit var importMenuItem: MenuItem
    @FXML
    private lateinit var importMultipleMenuItem: MenuItem
    @FXML
    private lateinit var exportMenuItem: MenuItem
    @FXML
    private lateinit var exportMultipleMenuItem: MenuItem
    @FXML
    private lateinit var helpMenuItem: MenuItem
    @FXML
    private lateinit var fitterMenuItem: MenuItem

    @FXML
    fun initialize() {
        importMenuItem.accelerator = KeyCodeCombination(I, SHORTCUT_DOWN)
        importMultipleMenuItem.accelerator = KeyCodeCombination(I, SHORTCUT_DOWN, SHIFT_DOWN)
        exportMenuItem.accelerator = KeyCodeCombination(E, SHORTCUT_DOWN)
        exportMultipleMenuItem.accelerator = KeyCodeCombination(E, SHORTCUT_DOWN, SHIFT_DOWN)
        fitterMenuItem.accelerator = KeyCodeCombination(F, SHORTCUT_DOWN)

        importMenuItem.setOnAction {
            val file = initFileChooser(".").showOpenDialog(rootController.mainApp.primaryStage)
            if (file != null) {
                rootController.mainController.lineChartController.importFrom(file)
            }
        }

        importMultipleMenuItem.setOnAction {
            val files = initFileChooser(".").showOpenMultipleDialog(rootController.mainApp.primaryStage)
            if (files != null) {
                rootController.mainController.lineChartController.importMultiple(files)
            }
        }

        exportMenuItem.setOnAction {
            val file = initFileChooser(".").let {
                it.initialFileName = buildExportFileName()
                it.showSaveDialog(rootController.mainApp.primaryStage)
            }
            if (file != null) {
                writeComputedDataTo(file)
            }
        }

        exportMultipleMenuItem.setOnAction {
            val page = with(FXMLLoader()) {
                location = MainApp::class.java.getResource("fxml/MultipleExportDialog.fxml")
                load<AnchorPane>()
            }
            with(Stage()) {
                title = "Export Multiple"
                isResizable = false
                scene = Scene(page)
                /* works after pressing directory button or switching between angle and temperature regimes. Why? */
                addEventHandler(KeyEvent.KEY_RELEASED) { event: KeyEvent ->
                    if (KeyCode.ESCAPE == event.code) {
                        close()
                    }
                }
                show()
            }
        }

        helpMenuItem.setOnAction {
            val page = with(FXMLLoader()) {
                location = MainApp::class.java.getResource("fxml/HelpInfo.fxml")
                load<AnchorPane>()
            }
            with(Stage()) {
                title = "Help"
                scene = Scene(page)
                /* works after pressing directory button or switching between angle and temperature regimes. Why? */
                addEventHandler(KeyEvent.KEY_RELEASED) { event: KeyEvent ->
                    if (KeyCode.ESCAPE == event.code) {
                        close()
                    }
                }
                showAndWait()
            }
        }
    }

    private fun initFileChooser(dir: String) = FileChooser().apply {
        extensionFilters.add(FileChooser.ExtensionFilter("Data Files", "*.txt", "*.dat"))
        initialDirectory = File(dir)
    }
}

class MultipleExportDialogController {

    lateinit var mainController: MainController

    @FXML
    private lateinit var polarizationChoiceBox: ChoiceBox<String>

    @FXML
    lateinit var angleFromTextField: TextField
    @FXML
    lateinit var angleToTextField: TextField
    @FXML
    lateinit var angleStepTextField: TextField

    @FXML
    private lateinit var directoryButton: Button
    @FXML
    private lateinit var exportButton: Button
    @FXML
    private lateinit var statusLabel: Label
    @FXML
    private lateinit var chosenDirectoryLabel: Label

    var angleFrom: Double = 0.0
    var angleTo: Double = 0.0
    var angleStep: Double = 0.0

    var chosenDirectory: File? = null

    @FXML
    fun initialize() {
        println("multiple export dialog controller init")

//        /* the only way to call another controllers of GUI is using already initialized State.mainController */
//        State.mainController.multipleExportDialogController = this
        mainController.multipleExportDialogController = this

        with(polarizationChoiceBox) {
            value = mainController.globalParametersController.lightParametersController.polarizationChoiceBox.value
            selectionModel.selectedItemProperty().addListener { _, _, newValue ->
                ComputationParametersStorage.polarization = newValue
                State.polarization = Polarization.valueOf(ComputationParametersStorage.polarization)

                mainController.globalParametersController.lightParametersController.polarizationChoiceBox.value = value
            }


//            value = State.polarization.toString()
//            selectionModel.selectedItemProperty().addListener { _, _, _ ->
//                State.polarization = Polarization.valueOf(value)
//                State.mainController.globalParametersController.lightParametersController.polarizationChoiceBox.value = value
//            }
        }

        directoryButton.setOnMouseClicked {
            with(DirectoryChooser()) {
                initialDirectory = File(".")
                /**
                Need to pass Window or Stage. There's no access to any Stage object from this controller
                Solution: any Node from fxml that has fx:id
                http://stackoverflow.com/questions/25491732/how-do-i-open-the-javafx-filechooser-from-a-controller-class
                 */
                chosenDirectory = showDialog(directoryButton.scene.window)
            }
            chosenDirectory?.let { chosenDirectoryLabel.text = it.canonicalPath }
        }


        /**
         * TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
         * Установить соответствующие поля для поляризации и угла в главном окне в поле light parameters из MultipleExportDialog
         * и вызывать State.init(), который будет запускать валидацию. Тут ничего не надо проверять
         */






        exportButton.setOnMouseClicked {
            with(MultipleExportDialogParametersValidator) {
                if (validateChosenDirectory() == SUCCESS) {
                    if (validateAngles() == SUCCESS) {
                        /**
                        Computation process runs through the setting fields in GUI (angle, polarization, etc.).
                        After that the validation takes place parsing these GUI fields and setting actual inner
                        parameters in program (State.angle, State.polarization, etc.).
                        To be able to compute and export data at multiple angles,
                        the corresponding GUI text field is init each time and validated and the computation process is performed.
                        In the end each field must get its initial value.
                         */
                        val initialAngle: String = State.mainController.globalParametersController
                                .lightParametersController.angleTextField.text
                        val initialPolarization = State.mainController.globalParametersController
                                .lightParametersController.polarizationChoiceBox.value

                        var currentAngle = angleFrom
                        while (currentAngle < 90.0 && currentAngle <= angleTo) {
                            with(State) {
                                /* angleTextField.text will be validated before computation */
                                mainController.globalParametersController.lightParametersController
                                        .angleTextField.text = currentAngle.toString()
                                init()
                                compute()
                            }
                            writeComputedDataTo(File("${chosenDirectory!!.canonicalPath}$separator${buildExportFileName()}.txt"))
                            currentAngle += angleStep
                        }
                        with(State.mainController.globalParametersController.lightParametersController) {
                            angleTextField.text = initialAngle
                            polarizationChoiceBox.value = initialPolarization
                        }
                    }
                    statusLabel.text = "Exported"
                }
            }
        }
    }
}

class HelpInfoController {

    @FXML
    private lateinit var helpTextArea: TextArea

    @FXML
    fun initialize() {
        helpTextArea.text = FileUtils.readFileToString(Paths.get("./data/inner/help.txt").toFile())
    }
}

