package io.mosip.testrig.apirig.esignet.testscripts;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.internal.BaseTestMethod;
import org.testng.internal.TestResult;

import io.mosip.testrig.apirig.dto.OutputValidationDto;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.esignet.utils.EsignetConfigManager;
import io.mosip.testrig.apirig.esignet.utils.EsignetUtil;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.testrunner.HealthChecker;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.AuthenticationTestException;
import io.mosip.testrig.apirig.utils.BioDataUtility;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.restassured.response.Response;

public class EsignetBioAuth extends EsignetUtil implements ITest {
	private static final Logger logger = Logger.getLogger(EsignetBioAuth.class);
	protected String testCaseName = "";
	public Response response = null;
	public boolean isInternal = false;

	private BioDataUtility bioDataUtil = new BioDataUtility();
	
	@BeforeClass
	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	/**
	 * get current testcaseName
	 */
	@Override
	public String getTestName() {
		return testCaseName;
	}

	/**
	 * Data provider class provides test case list
	 * 
	 * @return object of data provider
	 */
	@DataProvider(name = "testcaselist")
	public Object[] getTestCaseList(ITestContext context) {
		String ymlFile = context.getCurrentXmlTest().getLocalParameters().get("ymlFile");
		isInternal = Boolean.parseBoolean(context.getCurrentXmlTest().getLocalParameters().get("isInternal"));
		logger.info("Started executing yml: " + ymlFile);
		return getYmlTestData(ymlFile);
	}

	/**
	 * Test method for OTP Generation execution
	 * 
	 * @param objTestParameters
	 * @param testScenario
	 * @param testcaseName
	 * @throws AuthenticationTestException
	 * @throws AdminTestException
	 */
	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO) throws AuthenticationTestException, AdminTestException {
		testCaseName = testCaseDTO.getTestCaseName();		
		if (BaseTestCase.currentModule.equals(GlobalConstants.MASTERDATA)== false) {
			testCaseName = EsignetUtil.isTestCaseValidForExecution(testCaseDTO);
		}
		if (HealthChecker.signalTerminateExecution) {
			throw new SkipException(
					GlobalConstants.TARGET_ENV_HEALTH_CHECK_FAILED + HealthChecker.healthCheckFailureMapS);
		}

		if (testCaseDTO.getTestCaseName().contains("uin") || testCaseDTO.getTestCaseName().contains("UIN")) {
			if (!BaseTestCase.getSupportedIdTypesValue().contains("UIN")
					&& !BaseTestCase.getSupportedIdTypesValue().contains("uin")) {
				throw new SkipException(GlobalConstants.UIN_FEATURE_NOT_SUPPORTED);
			}
		}

		if (testCaseDTO.getTestCaseName().contains("VID") || testCaseDTO.getTestCaseName().contains("Vid")) {
			if (!BaseTestCase.getSupportedIdTypesValue().contains("VID")
					&& !BaseTestCase.getSupportedIdTypesValue().contains("vid")) {
				throw new SkipException(GlobalConstants.VID_FEATURE_NOT_SUPPORTED);
			}
		}
		JSONObject request = new JSONObject(testCaseDTO.getInput());
		String identityRequest = null;
		String identityRequestTemplate = null;
		String identityRequestEncUrl = null;
		if (request.has(GlobalConstants.IDENTITYREQUEST)) {
			identityRequest = request.get(GlobalConstants.IDENTITYREQUEST).toString();
			request.remove(GlobalConstants.IDENTITYREQUEST);
		}
		identityRequest = buildIdentityRequest(identityRequest);
		identityRequest = inputJsonKeyWordHandeler(identityRequest, testCaseName);

		JSONObject identityReqJson = new JSONObject(identityRequest);
		identityRequestTemplate = identityReqJson.getString("identityRequestTemplate");
		identityReqJson.remove("identityRequestTemplate");
		identityRequestEncUrl = identityReqJson.getString("identityRequestEncUrl");
		identityReqJson.remove("identityRequestEncUrl");
		
		if (identityReqJson.has("transactionId")) {
			String oidcTransactionId = EsignetUtil.getAuthTransactionId(identityReqJson.getString("transactionId"));
			if (oidcTransactionId != null && !oidcTransactionId.isBlank())
				identityReqJson.put("transactionId", oidcTransactionId);
		}
		
		identityRequest = getJsonFromTemplate(identityReqJson.toString(), identityRequestTemplate);
		if (identityRequest.contains("$DOMAINURI$")) {
			String domainUrl = EsignetConfigManager.getEsignetBaseUrl();
			identityRequest = identityRequest.replace("$DOMAINURI$", domainUrl);
		}
		
		logger.info("identityRequest = " + identityRequest);
		String encryptedIdentityReq = null;
		try {
			encryptedIdentityReq = bioDataUtil.constractBioIdentityRequest(identityRequest,
					getResourcePath() + properties.getProperty("bioValueEncryptionTemplate"), testCaseName, isInternal);
			
			logger.info("encryptedIdentityReq = " + encryptedIdentityReq);

			if (encryptedIdentityReq == null)
				throw new AdminTestException("bioDataUtil.constractBioIdentityRequest is null");

			JSONObject encryptedIdentityReqObject = new JSONObject(encryptedIdentityReq);

			logger.info("encryptedIdentityReqObject = " + encryptedIdentityReqObject);
			JSONArray arrayBiometrics = encryptedIdentityReqObject.getJSONArray(GlobalConstants.BIOMETRICS);

			String bioData = arrayBiometrics.toString();
			logger.info(bioData);

			byte[] byteBioData = bioData.getBytes();

			String challengeValue = Base64.getUrlEncoder().encodeToString(byteBioData);
			logger.info(challengeValue);

			String authRequest = getJsonFromTemplate(request.toString(), testCaseDTO.getInputTemplate());

			if (authRequest.contains("$CHALLENGE$")) {
				authRequest = authRequest.replace("$CHALLENGE$", challengeValue);
			}
			
			authRequest = EsignetUtil.inputstringKeyWordHandeler(authRequest, testCaseName);
			if (testCaseName.contains("ESignet_")) {
				String tempUrl = EsignetConfigManager.getEsignetBaseUrl();
				response = postRequestWithCookieAuthHeaderAndXsrfToken(tempUrl + testCaseDTO.getEndPoint(), authRequest,
						COOKIENAME, testCaseDTO.getTestCaseName());

			} else {
				response = postWithBodyAndCookie(ApplnURI + testCaseDTO.getEndPoint(), authRequest, COOKIENAME,
						testCaseDTO.getRole(), testCaseDTO.getTestCaseName());
			}
			String ActualOPJson = getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate());

			if (testCaseDTO.getTestCaseName().contains("uin") || testCaseDTO.getTestCaseName().contains("UIN")) {
				if (BaseTestCase.getSupportedIdTypesValue().contains("UIN")
						|| BaseTestCase.getSupportedIdTypesValue().contains("uin")) {
					ActualOPJson = getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate());
				} else {
					ActualOPJson = AdminTestUtil.getRequestJson("config/errorUINIdp.json").toString();
				}
			} else {
				if (testCaseDTO.getTestCaseName().contains("VID") || testCaseDTO.getTestCaseName().contains("Vid")) {
					if (BaseTestCase.getSupportedIdTypesValue().contains("VID")
							|| BaseTestCase.getSupportedIdTypesValue().contains("vid")) {
						ActualOPJson = getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate());
					} else {
						ActualOPJson = AdminTestUtil.getRequestJson("config/errorUINIdp.json").toString();
					}
				}
			}

			Map<String, List<OutputValidationDto>> ouputValid = OutputValidationUtil
					.doJsonOutputValidation(response.asString(), ActualOPJson, testCaseDTO, response.getStatusCode());
			Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));

			if (!OutputValidationUtil.publishOutputResult(ouputValid))
				throw new AdminTestException("Failed at output validation");
		} catch (SkipException e) {
			throw new SkipException(e.getMessage());
		} catch (Exception e) {
			throw new AdminTestException(e.getMessage());
		}

	}

	/**
	 * The method ser current test name to result
	 * 
	 * @param result
	 */
	@AfterMethod(alwaysRun = true)
	public void setResultTestName(ITestResult result) {
		try {
			Field method = TestResult.class.getDeclaredField("m_method");
			method.setAccessible(true);
			method.set(result, result.getMethod().clone());
			BaseTestMethod baseTestMethod = (BaseTestMethod) result.getMethod();
			Field f = baseTestMethod.getClass().getSuperclass().getDeclaredField("m_methodName");
			f.setAccessible(true);
			f.set(baseTestMethod, testCaseName);
		} catch (Exception e) {
			Reporter.log("Exception : " + e.getMessage());
		}

	}

}
