package de.diedavids.cuba.runtimediagnose.web.screens.wizard

import com.haulmont.cuba.core.global.DatatypeFormatter
import com.haulmont.cuba.core.global.GlobalConfig
import com.haulmont.cuba.core.global.TimeSource
import com.haulmont.cuba.gui.components.*
import com.haulmont.cuba.gui.export.ExportDisplay
import com.haulmont.cuba.gui.upload.FileUploadingAPI
import de.diedavids.cuba.runtimediagnose.diagnose.DiagnoseExecution
import de.diedavids.cuba.runtimediagnose.diagnose.DiagnoseExecutionFactory
import de.diedavids.cuba.runtimediagnose.groovy.GroovyDiagnoseService
import de.diedavids.cuba.runtimediagnose.sql.SqlDiagnoseService
import de.diedavids.cuba.runtimediagnose.web.screens.diagnose.DiagnoseFileDownloader
import de.diedavids.cuba.runtimediagnose.wizard.DiagnoseWizardResultType

import javax.inject.Inject

class DiagnoseWizard extends AbstractWindow {

    public static final String WIZARD_STEP_2 = 'step2'
    public static final String WIZARD_STEP_1 = 'step1'
    public static final String WIZARD_STEP_3 = 'step3'


    @Inject Accordion wizardAccordion
    @Inject Table diagnoseFileValidationTable
    @Inject Table diagnoseWizardResultsTable
    @Inject DiagnoseFileValidationDatasource diagnoseFileValidationDs
    @Inject DiagnoseExecutionResultDatasource diagnoseWizardResultsDs
    @Inject Button executeDiagnosisBtn
    @Inject Button closeWizard

    @Inject FileUploadField consoleFileUploadBtn
    @Inject FileUploadingAPI fileUploadingAPI
    @Inject GlobalConfig globalConfig
    @Inject ExportDisplay exportDisplay
    @Inject DatatypeFormatter datatypeFormatter
    @Inject TimeSource timeSource

    @Inject GroovyDiagnoseService groovyDiagnoseService
    @Inject SqlDiagnoseService sqlDiagnoseService
    @Inject DiagnoseExecutionFactory diagnoseExecutionFactory
    @Inject DiagnoseFileDownloader diagnoseFileDownloader

    DiagnoseExecution diagnoseExecution

    @Override
    void init(Map<String, Object> params) {

        def iconProvider = new DiagnoseWizardResultTypeIconProvider()
        diagnoseFileValidationTable.iconProvider = iconProvider
        diagnoseWizardResultsTable.iconProvider = iconProvider

        initUploadFileSucceedListener()
        initUploadFileErrorListener()
    }

    protected initUploadFileSucceedListener() {
        consoleFileUploadBtn.addFileUploadSucceedListener(new FileUploadField.FileUploadSucceedListener() {
            @Override
            void fileUploadSucceed(FileUploadField.FileUploadSucceedEvent e) {
                File file = fileUploadingAPI.getFile(consoleFileUploadBtn.fileId)

                diagnoseExecution = diagnoseExecutionFactory.createDiagnoseExecutionFromFile(file)
                diagnoseFileValidationDs.refresh([diagnose: diagnoseExecution])

                wizardAccordion.getTab(WIZARD_STEP_2).enabled = true
                wizardAccordion.tab = WIZARD_STEP_2

                if (diagnoseFileValidationDs.items.any { it.type == DiagnoseWizardResultType.ERROR }) {
                    executeDiagnosisBtn.enabled = false
                }
                Accordion.Tab step1 = wizardAccordion.getTab(WIZARD_STEP_1)
                step1.caption = "${step1.caption} $check"
                step1.enabled = false

            }
        })
    }

    protected initUploadFileErrorListener() {
        consoleFileUploadBtn.addFileUploadErrorListener(new UploadField.FileUploadErrorListener() {
            @Override
            void fileUploadError(UploadField.FileUploadErrorEvent e) {
                showNotification(formatMessage('fileUploadError'), Frame.NotificationType.ERROR)
            }
        })
    }

    void runGroovyDiagnose() {
        diagnoseExecution = groovyDiagnoseService.runGroovyDiagnose(diagnoseExecution)
        diagnoseWizardResultsDs.refresh([diagnose: diagnoseExecution])
    }


    void runSqlDiagnose() {
        diagnoseExecution = sqlDiagnoseService.runSqlDiagnose(diagnoseExecution)
        diagnoseWizardResultsDs.refresh([diagnose: diagnoseExecution])
    }

    void executeDiagnosis() {
        if (diagnoseExecution.isGroovy()) {
            runGroovyDiagnose()
        }
        else if( diagnoseExecution.isSQL()) {
            runSqlDiagnose()
        }
        progressToStep3()
    }

    protected void progressToStep3() {
        wizardAccordion.getTab(WIZARD_STEP_3).enabled = true
        wizardAccordion.tab = WIZARD_STEP_3
        closeWizard.enabled = true

        Accordion.Tab step2 = wizardAccordion.getTab(WIZARD_STEP_2)
        step2.caption = "$step2.caption $check"
        step2.enabled = false
    }

    protected String getCheck() {
        formatMessage('check')
    }

    void cancelWizard() {
        close(CLOSE_ACTION_ID)
    }

    void closeWizard() {
        downloadDiagnoseResult()
        close(CLOSE_ACTION_ID)
    }

    void downloadDiagnoseResult() {
        def zipBytes = diagnoseExecutionFactory.createExecutionResultFromDiagnoseExecution(diagnoseExecution)
        diagnoseFileDownloader.downloadFile(this, zipBytes)
    }

    @Override
    protected boolean preClose(String actionId) {

        if (diagnoseExecution?.pending) {
            showOptionDialog(formatMessage('cancelDiagnoseTitle'), formatMessage('cancelDiagnoseMessage'), Frame.MessageType.CONFIRMATION, [new DialogAction(DialogAction.Type.OK) {
                @Override
                void actionPerform(Component component) {
                    close(actionId, true)
                }
            }, new DialogAction(DialogAction.Type.CANCEL)])
        } else {
            close(actionId, true)
        }
        false
    }

}