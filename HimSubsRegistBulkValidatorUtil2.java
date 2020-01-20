package jp.go.mhlw.mil.him.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

import jp.go.mhlw.mil.cmn.bean.SystemInfoBean;
import jp.go.mhlw.mil.cmn.constant.CmnMsgConstant;
import jp.go.mhlw.mil.cmn.constant.MilDtlCodeConstant;
import jp.go.mhlw.mil.cmn.constant.MilPropConstant;
import jp.go.mhlw.mil.cmn.exception.MilBusinessException;
import jp.go.mhlw.mil.cmn.exception.MilException;
import jp.go.mhlw.mil.cmn.exception.MilInputErrorException;
import jp.go.mhlw.mil.cmn.exception.MilSystemException;
import jp.go.mhlw.mil.cmn.util.CheckUtil;
import jp.go.mhlw.mil.cmn.util.EntityManagerUtil;
import jp.go.mhlw.mil.cmn.util.SettingUtil;
import jp.go.mhlw.mil.cmn.util.StringUtil;
import jp.go.mhlw.mil.him.bean.MInsuredProofInfoBean;
import jp.go.mhlw.mil.him.bean.MInsuredProofInfoInputBean;
import jp.go.mhlw.mil.him.bean.QualBean;
import jp.go.mhlw.mil.him.bean.SubsBean;
import jp.go.mhlw.mil.him.constant.HimConstant;
import jp.go.mhlw.mil.him.constant.HimDtlCodeConstant;
import jp.go.mhlw.mil.him.constant.HimMsgConstant;
import jp.go.mhlw.mil.him.constant.HimPropConstant;
import jp.go.mhlw.mil.him.logic.HimInsurerNumberLogic;
import jp.go.mhlw.mil.him.logic.HimSubsLogic;
import jp.go.mhlw.mil.him.logic.MInsuredProofInfoLogic;
import jp.go.mhlw.mil.him.logic.MuniCodeLogic;

/**
 * <dl>
 * <dd>クラス名：加入者情報一括登録ファイルバリデーションユーティリティ。</dd>
 * <dd>クラス説明：加入者情報一括登録ファイルの入力項目をチェックするクラスです。</dd>
 * <dd>備考：</dd>
 * </dl>
 * @version 1.00 2016/07/31
 * @author MIL Consortium
 */
public final class HimSubsRegistBulkValidatorUtil2 {

	/**
	 * <dl>
	 * <dd>メソッド名：コンストラクタ。</dd>
	 * <dd>クラス説明：初期化処理を行います。</dd>
	 * <dd>備考：PR_HIM_05_406</dd>
	 * </dl>
	 */
	private HimSubsRegistBulkValidatorUtil2() {}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイル入力項目チェック。</dd>
	 * <dd>メソッド説明：加入者情報一括登録ファイルの項目をチェックします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param subsRegistList 行データリスト
	 * @param subsFileNumber 加入者の開始行番号
	 * @param errorList 処理結果メッセージリスト
	 * @throws ParseException 想定外例外発生
	 * @throws MilException
	 */
	public static void check(List<List<String>> subsRegistList, int subsFileNumber,
		List<MilInputErrorException> errorList, EntityManager em,  SystemInfoBean sib) throws ParseException, MilException {

		// CSVのレコードに資格情報最新の喪失日
		String qualDisqualDate = null;

		// 同一レコード識別番号内資格情報存在フラグ
		boolean qualExist = false;

		// 同一レコード識別番号内基本情報以外コントロール情報存在フラグ
		boolean conExist = false;

		// 行データの数え用
		int lineNumber = 0;

		// レコード数の上限
		long qualLimit = SettingUtil.getSegmentLong(HimPropConstant.SUBSQUAL_REGISTLIMIT);

		//DATEフォーマット
		SimpleDateFormat sdf = new SimpleDateFormat(HimConstant.DATE_PARSE_FORMAT_YYYY_MM_DD);
		sdf.setLenient(false);

		// キー項目重複チェック用
		List<String> checkKeyDatas = new ArrayList<>();

		// 組み合わせ用カウンター
		int subCount = 0;

		// 入力チェック用例外
		MilInputErrorException ie = new MilInputErrorException();

		// レコード識別番号毎にサブリスト化する。
		String currentId = "currentId";
		List<Integer> indexlist = new ArrayList<>();
		for (int i = 0; i < subsRegistList.size(); i++) {
			if (!currentId.equals(subsRegistList.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_ID_NUMBER))) {
				// レコード識別番号が変わったら行番号をインデックスリストに追加する。
				indexlist.add(i);
				currentId = subsRegistList.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_ID_NUMBER);
			}
		}
		List<List<List<String>>> eachRecordIdList = new ArrayList<List<List<String>>>();
		for (int j = 0; j < indexlist.size(); j++) {
			// インデックスリストの行番号をもとにサブリストを作成する。
			if (j == indexlist.size() - 1) {
				eachRecordIdList.add(subsRegistList.subList(indexlist.get(j), subsRegistList.size()));
			} else {
				eachRecordIdList.add(subsRegistList.subList(indexlist.get(j), indexlist.get(j + 1)));
			}
		}
		Long segmentLong = SettingUtil.getSegmentLong(HimPropConstant.SUBSCRIVERLISTFILE_MAXCOUNT);
		// 加入者数上限チェック
		if (eachRecordIdList.size() > segmentLong.intValue()) {
			ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0134);
			errorList.add(ie);
			ie = new MilInputErrorException();
		}
		// サブリストの数でまわす。
		for (int k = 0; k < eachRecordIdList.size(); k++) {

			// 資格情報の比較用
			List<String> checkQualDatas = new ArrayList<>();

			// レコード種別の並び順判定用
			List<Integer> sortRecordList = new ArrayList<>();

			// 加入者基本情報変更履歴レコード数
			int subscriberHistoryCount = 0;
			// 加入者資格情報レコード数
			int qualificationCount = 0;
			// 被保険者証等情報レコード数
			int insuredProofInfoInputBeanCount = 0;
			// 高齢受給者証情報レコード数
			int elderlyInsuredProofInfoCount = 0;
			// 限度額適用認定証関連情報レコード数
			int limitOfCopaymentInputBeanCount = 0;
			// 特定疾病療養受療証情報レコード数
			int specificDiseaseInputBeanCount = 0;

			// フラグ初期化
			qualDisqualDate = null;
			qualExist = false;
			conExist = false;

			for (List<String> dataList2 : eachRecordIdList.get(k)) {
				// 加入者資格情報レコードチェック
				if (HimConstant.RECORD_TYPE_M_QUALIFICATION_CODE.equals(dataList2
						.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

					if(!HimStringCheckUtil.checkEmpty(dataList2.get(HimConstant
							.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE))) {
						if(!qualExist) {
							qualDisqualDate = dataList2.get(HimConstant
									.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE);
						} else if (qualDisqualDate != "" && (dataList2.get(HimConstant
								.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE)
								.compareTo(qualDisqualDate)) > 0) {
							qualDisqualDate = dataList2.get(HimConstant
									.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE);
						}
					} else {
						// 資格喪失日なし、""で判明する
						qualDisqualDate = "";
					}
					qualExist = true ;
				}
				if(!HimConstant.RECORD_TYPE_M_SUBSCRIBER_CODE.equals(dataList2
						.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
					conExist = true;
				}
			}

			// レコード識別番号サブリストでまわす。
			for (int l = 0; l < eachRecordIdList.get(k).size(); l++) {

                List<String> dataList = eachRecordIdList.get(k).get(l);

				// エラーメッセージ作成用行番号
				String strFileLineNumber = StringUtil.getCommaAmount((long)subsFileNumber + lineNumber);

				// システム基本情報項目の入力チェック
				// レコード識別番号のチェックをおこないます。
				checkRecordIdNumber(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_ID_NUMBER), strFileLineNumber, ie);
				// レコード種別コードのチェックをおこないます。
				checkRecordCategory(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY), strFileLineNumber, ie);
				// 処理種別コードのチェックをおこないます。
				checkProcessCategory(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY), strFileLineNumber, ie);

				if (l + 1 < eachRecordIdList.get(k).size()) {
				    // 処理中の加入者単位のリストの最終行でない場合
                    // 次の行の処理種別コードを取得する
                    String nextProcCd = eachRecordIdList.get(k).get(l + 1).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY);

                    // 処理中の処理種別コード次の行の処理種別コードを比較する
    				if (!dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY).equals(nextProcCd)) {
                        // 異なる処理種別が設定されていた場合、フォーマットエラーとし、該当の加入者のレコードチェックは終了する。
    					// 1人の加入者で処理種別コードが異なる場合
    					ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0117);
    					break;
					}
				}
				// 保険者コードのチェックをおこないます。
				checkInsurerCode(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE), strFileLineNumber, ie);
				// 被保険者枝番のチェックをおこないます。
				checkInsureBranchNumber(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURED_BRANCH_NUMBER),
						dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY), strFileLineNumber, ie);
				// 個人番号のチェックをおこないます。
				checkPersonalNumber(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PERSONAL_NUMBER),
						dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_AFTER_PERSONAL_NUMBER),
						dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY), strFileLineNumber, ie);
				// 更新後個人番号のチェックをおこないます。
				checkAfterPersonalNumber(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_AFTER_PERSONAL_NUMBER),
						dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PERSONAL_NUMBER),
						dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY), strFileLineNumber, ie);

				//種別削除の場合以外チェック
				if(!HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_DELETE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
					// 加入者基本情報レコードチェック
					if (HimConstant.RECORD_TYPE_M_SUBSCRIBER_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

						// 入力項目数が一致しない行はフォーマットエラー。
						if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_M_SUBSCRIBER_DATA + HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
						}else {
							// 項目チェック
							checkSubscriberList(dataList, lineNumber, strFileLineNumber, ie, em, sib, sdf, conExist);
						}
					}

					// 情報提供に関する制御情報レコードチェック
					if (HimConstant.RECORD_TYPE_M_INFO_SERV_CTRL_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

						// 入力項目数が一致しない行はフォーマットエラー。
						if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_M_INFO_SERV_CTRL + HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
						}else {
							// 項目チェック
							checkInfoServCtrlList(dataList, lineNumber, strFileLineNumber, ie, sdf);
						}
					}

					// 加入者基本情報変更履歴レコードチェック
					if (HimConstant.RECORD_TYPE_M_SUBSCRIBER_DATA_HISTORY_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

						// カウントアップ
						subscriberHistoryCount++;
						// 入力項目数が一致しない行はフォーマットエラー。
						if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_M_SUBSCRIBER_DATA_HISTORY + HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
						}else {
							// 項目チェック
							checkSubscriberHistoryList(dataList, lineNumber, strFileLineNumber, ie, em, sib, sdf);
						}
					}

					// 加入者資格情報レコードチェック
					if (HimConstant.RECORD_TYPE_M_QUALIFICATION_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

						// カウントアップ
						qualificationCount++;
						// 入力項目数が一致しない行はフォーマットエラー。
						if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_M_QUAL_INFO + HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
						}else {
							// 項目チェック
							checkQualificationList(dataList, checkQualDatas, lineNumber, strFileLineNumber, ie, sdf);
						}
					}

					// 被保険者証等情報レコードチェック
					if (HimConstant.RECORD_TYPE_M_INSURED_PROOF_INFO_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

						// カウントアップ
						insuredProofInfoInputBeanCount++;
						// 入力項目数が一致しない行はフォーマットエラー。
						if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_M_INSURED_PROOF_INFO + HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
						}else {
							// 項目チェック
							checkInsuredProofInfoInputBeanList(dataList, lineNumber, strFileLineNumber, ie,qualDisqualDate, qualExist, sdf);
							// 保険者番号存在チェック
							checkExistsInsurerNumberInsuredProofInfo(dataList, lineNumber, strFileLineNumber, ie, em, sib);
							// 被保険者証情報の重複チェック
							checkDuplicateInsuredProofInfo(dataList, strFileLineNumber, ie, em, sib);
						}
					}

					// 高齢受給者証情報レコードチェック
					if (HimConstant.RECORD_TYPE_M_ELDERLY_INSURED_PROOF_INFO_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

						// カウントアップ
						elderlyInsuredProofInfoCount++;
						// 入力項目数が一致しない行はフォーマットエラー。
						if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_M_ELDERLY_INSURED_PROOF_INFO + HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
						}else {
							// 項目チェック
							checkElderlyInsuredProofInfoList(dataList, lineNumber, strFileLineNumber, ie,qualDisqualDate, qualExist, sdf);
							// 保険者番号存在チェック
							checkExistsInsurerNumberElderlyInsuredProofInfo(dataList, lineNumber, strFileLineNumber, ie, em, sib);
						}
					}

					// 限度額適用認定証関連情報レコードチェック
					if (HimConstant.RECORD_TYPE_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

						// カウントアップ
						limitOfCopaymentInputBeanCount++;
						// 入力項目数が一致しない行はフォーマットエラー。
						if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_M_LIMIT_OF_COPAYMENT_PROOF_INFO + HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
						}else {
							// 項目チェック
							checkLimitOfCopaymentInputBeanList(dataList, lineNumber, strFileLineNumber, ie,qualDisqualDate, qualExist, sdf);
							// 保険者番号存在チェック
							checkExistsInsurerNumberLimitOfCopayment(dataList, lineNumber, strFileLineNumber, ie, em, sib);
						}
					}

					// 特定疾病療養受療証情報レコードチェック
					if (HimConstant.RECORD_TYPE_M_SPECIFIC_DISEASE_PROOF_INFO_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

						// カウントアップ
						specificDiseaseInputBeanCount++;
						// 入力項目数が一致しない行はフォーマットエラー。
						if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_M_SPECIFIC_DISEASE_PROOF_INFO + HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
						}else {
							// 項目チェック
							checkSpecificDiseaseInputBeanList(dataList, lineNumber, strFileLineNumber, ie,qualDisqualDate, qualExist, sdf);
							// 保険者番号存在チェック
							checkExistsInsurerNumberSpecificDisease(dataList, lineNumber, strFileLineNumber, ie, em, sib);
						}
					}
				}else if(HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
					// 種別更新の場合、入力項目数が一致しない行はフォーマットエラー。システム基本情報部のみ
					if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
						ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
					}
				}

				// 加入者削除・個人番号変更情報レコードチェック
				if (HimConstant.RECORD_TYPE_M_SUBSCRIBER_DELETE_AND_PERSONAL_NUMBER_CHANGE_CODE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

					// 入力項目数が一致しない行はフォーマットエラー。システム基本情報部のみ
					if (dataList.size() != HimConstant.HIM_REGIST_FILE_ITEM_SYSTEM_BASIC_INFO) {
						ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0042, strFileLineNumber);
					}
				}

				// レコード種別の数値リストを作成する。
				switch (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY)) {
				case HimConstant.RECORD_TYPE_M_SUBSCRIBER_CODE:
					sortRecordList.add(1);
					break;
				case HimConstant.RECORD_TYPE_M_INFO_SERV_CTRL_CODE:
					sortRecordList.add(2);
					break;
				case HimConstant.RECORD_TYPE_M_SUBSCRIBER_DATA_HISTORY_CODE:
					sortRecordList.add(3);
					break;
				case HimConstant.RECORD_TYPE_M_QUALIFICATION_CODE:
					sortRecordList.add(4);
					break;
				case HimConstant.RECORD_TYPE_M_INSURED_PROOF_INFO_CODE:
					sortRecordList.add(5);
					break;
				case HimConstant.RECORD_TYPE_M_ELDERLY_INSURED_PROOF_INFO_CODE:
					sortRecordList.add(6);
					break;
				case HimConstant.RECORD_TYPE_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CODE:
					sortRecordList.add(7);
					break;
				case HimConstant.RECORD_TYPE_M_SPECIFIC_DISEASE_PROOF_INFO_CODE:
					sortRecordList.add(8);
					break;
				case HimConstant.RECORD_TYPE_M_SUBSCRIBER_DELETE_AND_PERSONAL_NUMBER_CHANGE_CODE:
					sortRecordList.add(9);
					break;
				default:
					break;
				}

				// 行番号を加算する
				lineNumber++;
				// カウントアップ
				subCount++;
			}

			// レコード上限チェック
			if (subscriberHistoryCount > qualLimit) {
				// 上限数エラーメッセージを出力する。
				ie.addErrMsgList(HimConstant.PROPERTY_HISTORY_INFO, HimMsgConstant.MSG_HIM_W0101,
						StringUtil.getCommaAmount((long)subsFileNumber), HimConstant.ITEM_SUBSCRIBER_DATA_HISTORY);
			}
			if (qualificationCount > qualLimit) {
				// 上限数エラーメッセージを出力する。
				ie.addErrMsgList(HimConstant.PROPERTY_QUAL_INFO, HimMsgConstant.MSG_HIM_W0101,
						StringUtil.getCommaAmount((long)subsFileNumber), HimConstant.ITEM_SUBSCRIBER_QUALIFICATION);
			}
			if (insuredProofInfoInputBeanCount > qualLimit) {
				// 上限数エラーメッセージを出力する。
				ie.addErrMsgList(HimConstant.PROPERTY_INSURED_PROOF_INFO, HimMsgConstant.MSG_HIM_W0101,
						StringUtil.getCommaAmount((long)subsFileNumber), HimConstant.ITEM_INSURED_PROOF_INFO);
			}
			if (elderlyInsuredProofInfoCount > qualLimit) {
				// 上限数エラーメッセージを出力する。
				ie.addErrMsgList(HimConstant.PROPERTY_ELDERLY_INSURED_INFO, HimMsgConstant.MSG_HIM_W0101,
						StringUtil.getCommaAmount((long)subsFileNumber), HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO);
			}
			if (limitOfCopaymentInputBeanCount > qualLimit) {
				// 上限数エラーメッセージを出力する。
				ie.addErrMsgList(HimConstant.PROPERTY_LIMIT_OF_COPAYMENT_INFO, HimMsgConstant.MSG_HIM_W0101,
						StringUtil.getCommaAmount((long)subsFileNumber), HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO);
			}
			if (specificDiseaseInputBeanCount > qualLimit) {
				// 上限数エラーメッセージを出力する。
				ie.addErrMsgList(HimConstant.PROPERTY_SPECIFIC_DISEASE_INFO, HimMsgConstant.MSG_HIM_W0101,
						StringUtil.getCommaAmount((long)subsFileNumber), HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO);
			}

			// 組み合わせ判定用処理種別コード
			String processCategory = eachRecordIdList.get(k).get(0).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY);
			// 組み合わせ判定用加入者区分
			String segmentOfSubscriber = null;
			if("SD".equals(eachRecordIdList.get(k).get(0).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
				segmentOfSubscriber = eachRecordIdList.get(k).get(0).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_SEGMENT_OF_SUBSCRIBER);
			}
			// レコード種別の組み合わせ判定
			checkRecordCombination(segmentOfSubscriber, processCategory, sortRecordList, subCount, ie,subsFileNumber);
			// キー項目重複チェック
			checkKeyColumnOverlap(eachRecordIdList.get(k), checkKeyDatas, errorList, ie);

			// レコード種別の並び順判定
			checkSortRecordCategory(sortRecordList, errorList, ie);
			// 保険者番号・記号・番号・枝番紐づきチェック
			insurerTyingCheck(eachRecordIdList.get(k),sortRecordList,subsFileNumber,ie, em, sib);

			errorList.add(ie);
		}
	}



	/**
	 * 保険者番号・記号・番号・枝番紐づきチェック
	 *
	 * @param eachlist データリスト
	 * @param sortRecordList 並び順リスト
	 * @param subsFileNumber 行番号
	 * @param ie
	 */
	private static void insurerTyingCheck(List<List<String>> eachlist,List<Integer> sortRecordList,int subsFileNumber,
			MilInputErrorException ie, EntityManager em, SystemInfoBean sib) {
		// 被保険者情報の他テーブル紐づけ項目保管用のリスト
		List<String> insuredInfoTyingList = new ArrayList<String>();
		int rowNumber = subsFileNumber;

		for (int i = 0; i < eachlist.size(); i++) {
			//被保険者等証情報レコード
			if(HimConstant.RECORD_TYPE_M_INSURED_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
				StringBuilder insurerSb = new StringBuilder();
				String cardInsurerNumber = eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER);
				String cardinsuredProofSymbol = eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL);
				String cardInsuredProofNumber = eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER);
				String cardBranchNumber = eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER);
				insurerSb.append(HimStringCheckUtil.checkEmpty(cardInsurerNumber) ? "" : cardInsurerNumber + ",")
				.append(HimStringCheckUtil.checkEmpty(cardinsuredProofSymbol) ? "" : cardinsuredProofSymbol + ",")
				.append(HimStringCheckUtil.checkEmpty(cardInsuredProofNumber) ? "" : cardInsuredProofNumber + ",")
				.append(HimStringCheckUtil.checkEmpty(cardBranchNumber) ? "" : cardBranchNumber);
				insuredInfoTyingList.add(insurerSb.toString());
			}
		}
		for (int i = 0; i < eachlist.size(); i++) {
			//(新)加入者情報の登録、(新)加入者情報のレコード種別単位更新、(新)加入者の全体更新の場合実行します。
			if(HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
					HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
					HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {

				//加入者情報のレコード種別単位更新の場合、処理中の加入者情報に被保険者証情報がない。
				if(HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) &&
						!sortRecordList.contains(5)) {

					//高齢受給者証情報DB比べる。
					if(HimConstant.RECORD_TYPE_M_ELDERLY_INSURED_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
						insuredTyingDbContainsCheck(insuredInfoTyingList, eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER),
								rowNumber, ie, HimConstant.PROPERTY_ELDERLY_INSURED_INFO,HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO,
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURED_BRANCH_NUMBER), em, sib);
					}
					//限度額適用認定証関連情報DB比べる。
					if(HimConstant.RECORD_TYPE_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
						insuredTyingDbContainsCheck(insuredInfoTyingList, eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_BRANCH_NUMBER),
								rowNumber, ie, HimConstant.PROPERTY_LIMIT_OF_COPAYMENT_INFO,HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO,
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURED_BRANCH_NUMBER), em, sib);
					}
					//特定疾病療養受療証情報DB比べる。
					if(HimConstant.RECORD_TYPE_M_SPECIFIC_DISEASE_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
						insuredTyingDbContainsCheck(insuredInfoTyingList, eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_BRANCH_NUMBER),
								rowNumber, ie, HimConstant.PROPERTY_SPECIFIC_DISEASE_INFO,HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO,
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURED_BRANCH_NUMBER), em, sib);
					}
				}else {
					//高齢受給者証情報csv比べる。
					if(HimConstant.RECORD_TYPE_M_ELDERLY_INSURED_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
						insuredTyingCsvContainsCheck(insuredInfoTyingList, eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER),
								rowNumber, ie, HimConstant.PROPERTY_ELDERLY_INSURED_INFO,HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO);
					}
					//限度額適用認定証関連情報csv比べる。
					if(HimConstant.RECORD_TYPE_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
						insuredTyingCsvContainsCheck(insuredInfoTyingList, eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_BRANCH_NUMBER),
								rowNumber, ie, HimConstant.PROPERTY_LIMIT_OF_COPAYMENT_INFO,HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO);
					}
					//特定疾病療養受療証情報csv比べる。
					if(HimConstant.RECORD_TYPE_M_SPECIFIC_DISEASE_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {
						insuredTyingCsvContainsCheck(insuredInfoTyingList, eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_NUMBER),
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_BRANCH_NUMBER),
								rowNumber, ie, HimConstant.PROPERTY_SPECIFIC_DISEASE_INFO,HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO);
					}
				}
				rowNumber++;
			}
		}
	}

	/**
	 * @param insuredInfoTyingList 被保険者情報の他テーブル紐づけ項目保管用のリスト
	 * @param cardInsurerNumber 保険者番号(高齢受給者証情報、限度額適用認定証関連情報、特定疾病療養受療証情報)
	 * @param cardInsuredProofSymbol 被保険者記号(高齢受給者証情報、限度額適用認定証関連情報、特定疾病療養受療証情報)
	 * @param cardInsuredProofNumber 被保険者番号(高齢受給者証情報、限度額適用認定証関連情報、特定疾病療養受療証情報)
	 * @param cardBranchNumber 被保険者枝番(高齢受給者証情報、限度額適用認定証関連情報、特定疾病療養受療証情報)
	 * @param rowNumber 行番号
	 * @param ie
	 * @param property 入力エラー項目
	 * @param parameter メッセージパラメータ
	 * @param insurerCode 保険者コード
	 * @param insuredBranchNumber 被保険者枝番
	 */
	public static void insuredTyingDbContainsCheck(List<String> insuredInfoTyingList,String cardInsurerNumber,
			String cardInsuredProofSymbol,String cardInsuredProofNumber,String cardBranchNumber,
			int rowNumber,MilInputErrorException ie,String property,String parameter,String insurerCode,String insuredBranchNumber,
			EntityManager em, SystemInfoBean sib) {
		// 限度額適用認定証関連情報マスタ削除
		Query mInsuredProofInfoquery = em.createNamedQuery("m_insured_proof_infoS1");

		mInsuredProofInfoquery.setParameter("insurer_code", insurerCode);
		mInsuredProofInfoquery.setParameter("insured_branch_number", insuredBranchNumber);
		@SuppressWarnings("unchecked")
		List<Object[]> resultmInsuredProofInfoList = (List<Object[]>)mInsuredProofInfoquery
				.getResultList();
		List<MInsuredProofInfoBean> mInsuredProofInfoBeanList = null;
		// DBまたはCSVに紐づく被保険者証等情報があるか確認する。
		if (resultmInsuredProofInfoList.size() != 0) {
			// 被保険者証等情報ロジック
			MInsuredProofInfoLogic mInsuredProofInfoLogic = new MInsuredProofInfoLogic(em, sib);

			// DBから取得した被保険者証等情報リストを設定します。
			mInsuredProofInfoBeanList = mInsuredProofInfoLogic.buildMInsuredProofInfoBeanList(resultmInsuredProofInfoList);
			// 保険者番号・記号・番号・枝番紐づきチェック
			String checkString = "";
			for (int i = 0; i < resultmInsuredProofInfoList.size(); i++) {
				checkString = checkString + mInsuredProofInfoBeanList.get(i).getCardInsurerNumber() + ","
						+ mInsuredProofInfoBeanList.get(i).getCardinsuredProofSymbol()  + ","
						+ mInsuredProofInfoBeanList.get(i).getCardInsuredProofNumber() + ","
						+ mInsuredProofInfoBeanList.get(i).getCardBranchNumber() + ";";
			}
			// 保険者番号、被保険者記号・番号・枝番を連結し、被保険者証情報と比較する。
			StringBuilder elderlySb = new StringBuilder();
			elderlySb.append(HimStringCheckUtil.checkEmpty(cardInsurerNumber) ? "" : cardInsurerNumber + ",")
			.append(HimStringCheckUtil.checkEmpty(cardInsuredProofSymbol) ? "" : cardInsuredProofSymbol + ",")
			.append(HimStringCheckUtil.checkEmpty(cardInsuredProofNumber) ? "" : cardInsuredProofNumber + ",")
			.append(HimStringCheckUtil.checkEmpty(cardBranchNumber) ? "" : cardBranchNumber);

			if(!checkString.contains(elderlySb.toString())) {
				// 連結した値が被保険者情報の紐づけ項目保管用のリストに存在しない場合エラー
				ie.addErrMsgList(property, HimMsgConstant.MSG_HIM_W0152,String.valueOf(rowNumber),parameter);
			}
		}else {
			ie.addErrMsgList(property, HimMsgConstant.MSG_HIM_W0119,String.valueOf(rowNumber),parameter);
		}
	}
	/**
	 * csvの被保険者と比較の紐づきチェック。
	 *
	 * @param insuredInfoTyingList 被保険者情報の他テーブル紐づけ項目保管用のリスト
	 * @param cardInsurerNumber 保険者番号(高齢受給者証情報、限度額適用認定証関連情報、特定疾病療養受療証情報)
	 * @param cardInsuredProofSymbol 被保険者記号(高齢受給者証情報、限度額適用認定証関連情報、特定疾病療養受療証情報)
	 * @param cardInsuredProofNumber 被保険者番号(高齢受給者証情報、限度額適用認定証関連情報、特定疾病療養受療証情報)
	 * @param cardBranchNumber 被保険者枝番(高齢受給者証情報、限度額適用認定証関連情報、特定疾病療養受療証情報)
	 * @param rowNumber 行番号
	 * @param ie
	 * @param property 入力エラー項目
	 * @param parameter メッセージパラメータ
	 */
	public static void insuredTyingCsvContainsCheck(List<String> insuredInfoTyingList,String cardInsurerNumber,
			String cardInsuredProofSymbol,String cardInsuredProofNumber,String cardBranchNumber,
			int rowNumber,MilInputErrorException ie,String property,String parameter) {
		// 保険者番号、被保険者記号・番号・枝番を連結し、被保険者証情報と比較する。
		StringBuilder elderlySb = new StringBuilder();
		elderlySb.append(HimStringCheckUtil.checkEmpty(cardInsurerNumber) ? "" : cardInsurerNumber + ",")
		.append(HimStringCheckUtil.checkEmpty(cardInsuredProofSymbol) ? "" : cardInsuredProofSymbol + ",")
		.append(HimStringCheckUtil.checkEmpty(cardInsuredProofNumber) ? "" : cardInsuredProofNumber + ",")
		.append(HimStringCheckUtil.checkEmpty(cardBranchNumber) ? "" : cardBranchNumber);

		if(insuredInfoTyingList.size()>0) {
			if(!insuredInfoTyingList.contains(elderlySb.toString())) {
				// 連結した値が被保険者情報の紐づけ項目保管用のリストに存在しない場合エラー
				ie.addErrMsgList(property, HimMsgConstant.MSG_HIM_W0152,String.valueOf(rowNumber),parameter);
			}
		}else {
			ie.addErrMsgList(property, HimMsgConstant.MSG_HIM_W0119,String.valueOf(rowNumber),parameter);
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：情報提供に関する制御情報レコード存在チェック。</dd>
	 * <dd>メソッド説明：情報提供に関する制御情報レコードを存在チェックします。</dd>
	 * <dd>備考：PR_HIM_01_202</dd>
	 * </dl>
	 * @param dataList
	 * @param checkQualDatas
	 * @param lineNumber
	 * @param strFileLineNumber
	 * @param ie
	 * @throws MilException
	 * @throws ParseException
	 */
	private static void checkInfoServCtrlList(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, SimpleDateFormat sdf) throws MilException{

		// 特定検診情報提供に係る本人同意フラグ本人(不)同意日
		String provideSpecificCheckupInformationDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INFO_SERV_CTRL_PROVIDE_SPECIFIC_CHECKUP_INFORMATION_DATE);
		String provideSpecificCheckupInformationFlag = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INFO_SERV_CTRL_PROVIDE_SPECIFIC_CHECKUP_INFORMATION_FLAG);
		if (!HimStringCheckUtil.checkEmpty(provideSpecificCheckupInformationFlag)) {
			if (!provideSpecificCheckupInformationFlag.matches(HimConstant.CHECK_PROVIDE_SPECIFIC_CHECK_UP_INFORMATION_FLAG)) {
				// 数値(0,1,2)
				ie.addErrMsgList(HimConstant.PROPERTY_PROVIDE_SPECIFIC_CHECKUP_INFORMATION_FLAG, CmnMsgConstant.MSG_CMN_W2125,
						strFileLineNumber, HimConstant.ITEM_PROVIDE_SPECIFIC_CHECK_UP_INFORMATION_FLAG);
			}
		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_PROVIDE_SPECIFIC_CHECKUP_INFORMATION_FLAG, CmnMsgConstant.MSG_CMN_W2124,
					strFileLineNumber, HimConstant.ITEM_PROVIDE_SPECIFIC_CHECK_UP_INFORMATION_FLAG);
		}
		// 本人同意フラグ本人(不)同意日のチェックをおこないます。
		if (provideSpecificCheckupInformationFlag.equals("1") || provideSpecificCheckupInformationFlag.equals("2")) {
			// [1:同意あり]、[2:同意なし]の場合必須
			if (HimStringCheckUtil.checkEmpty(provideSpecificCheckupInformationDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_PROVIDE_SPECIFIC_CHECKUP_INFORMATION_DATE, CmnMsgConstant.MSG_CMN_W2124,
						strFileLineNumber, HimConstant.ITEM_PROVIDE_SPECIFIC_CHECK_UP_INFORMATION_DATE);
			}
		} else {
			// 設定できない。
			if (!HimStringCheckUtil.checkEmpty(provideSpecificCheckupInformationDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_PROVIDE_SPECIFIC_CHECKUP_INFORMATION_DATE, HimMsgConstant.MSG_HIM_W0092,
						strFileLineNumber, HimConstant.ITEM_PROVIDE_SPECIFIC_CHECK_UP_INFORMATION_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(provideSpecificCheckupInformationDate)) {
			// 日付妥当性チェック
			if (!checkDateFormat(provideSpecificCheckupInformationDate, sdf)) {
                // 日付として妥当ではない場合
                setDateFormatError(
                    ie,
                    HimConstant.PROPERTY_PROVIDE_SPECIFIC_CHECKUP_INFORMATION_DATE,
                    HimConstant.ITEM_PROVIDE_SPECIFIC_CHECK_UP_INFORMATION_DATE);
			}
		}

		// 自己情報提供不可フラグ
		String doNotProvideSelfInfoFlag = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INFO_SERV_CTRL_DO_NOT_PROVIDE_SELF_INFO_FLAG);
		// 必須チェック
		if (HimStringCheckUtil.checkEmpty(doNotProvideSelfInfoFlag)) {
			ie.addErrMsgList(HimConstant.PROPERTY_DO_NOT_PROVIDE_SELF_INFO_FLAG, CmnMsgConstant.MSG_CMN_W2124,
					strFileLineNumber, HimConstant.ITEM_DONOT_PROVIDE_SELF_INFO_FLAG);
		} else if (!doNotProvideSelfInfoFlag.matches(HimConstant.CHECK_DONOT_PROVIDE_SELF_INFO_FLAG)) {
			// 数値(0,1,2)
			ie.addErrMsgList(HimConstant.PROPERTY_DO_NOT_PROVIDE_SELF_INFO_FLAG, CmnMsgConstant.MSG_CMN_W2125,
					strFileLineNumber, HimConstant.ITEM_DONOT_PROVIDE_SELF_INFO_FLAG);
		}

		// 不開示該当フラグ
		String undisclosedFlag = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INFO_SERV_CTRL_UNDISCLOSED_FLAG);
		if (!HimStringCheckUtil.checkEmpty(undisclosedFlag)) {
			if (!undisclosedFlag.matches(HimConstant.CHECK_UN_DISCLOSED_FLAG)) {
				// 数値(0,1,2)
				ie.addErrMsgList(HimConstant.PROPERTY_UNDISCLOSED_FLAG, CmnMsgConstant.MSG_CMN_W2125,
						strFileLineNumber, HimConstant.ITEM_UN_DISCLOSED_FLAG);
			}
		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_UNDISCLOSED_FLAG, CmnMsgConstant.MSG_CMN_W2124,
					strFileLineNumber, HimConstant.ITEM_UN_DISCLOSED_FLAG);
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者基本情報レコード存在チェック。</dd>
	 * <dd>メソッド説明：加入者基本情報レコードを存在チェックします。</dd>
	 * <dd>備考：PR_HIM_01_202</dd>
	 * </dl>
	 * @param dataList
	 * @param checkQualDatas
	 * @param lineNumber
	 * @param strFileLineNumber
	 * @param ie
	 * @throws MilException
	 * @throws ParseException
	 */
	private static void checkSubscriberList(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, EntityManager em,  SystemInfoBean sib, SimpleDateFormat sdf, boolean conExist) throws MilException{

		// 氏名(券面記載)のチェックをおこないます。
		String name = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_NAME);
		if (HimStringCheckUtil.checkEmpty(name)) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_NAME, CmnMsgConstant.MSG_CMN_W2124,
				strFileLineNumber, HimConstant.ITEM_NAME_NEW);
		} else if (!HimStringCheckUtil.checkMaxLength(name, HimConstant.LONG_NAME)) {
			// 100文字以内であること(氏名)
			ie.addErrMsgList(HimConstant.PROPERTY_NAME, CmnMsgConstant.MSG_CMN_W2129,
				strFileLineNumber, HimConstant.ITEM_NAME_NEW, String.valueOf(HimConstant.LONG_NAME));
		} else if (!HimStringCheckUtil.checkZenkaku(name)) {
			// 全角チェック
			ie.addErrMsgList(HimConstant.PROPERTY_NAME, CmnMsgConstant.MSG_CMN_W2228,
				strFileLineNumber, HimConstant.ITEM_NAME_NEW);
		}

		// 氏名(券面記載)(カナ)のチェックをおこないます。
		String nameKana = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_NAME_KANA);
		if (!HimStringCheckUtil.checkEmpty(nameKana)) {
			if (!HimStringCheckUtil.checkMaxLength(nameKana, HimConstant.LONG_NAME)) {
				// 100文字以内であること(氏名かな)
				ie.addErrMsgList(HimConstant.PROPERTY_NAME_KANA, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_NAME_KANA_NEW, String.valueOf(HimConstant.LONG_NAME));
			} else if (!HimStringCheckUtil.checkHankaku(nameKana)) {
				// 半角カナまたは半角英字、半角記号、半角空白
				ie.addErrMsgList(HimConstant.PROPERTY_NAME_KANA, HimMsgConstant.MSG_HIM_W0135,
					strFileLineNumber, HimConstant.ITEM_NAME_KANA_NEW);
			}
		}

		// 氏名(その他)のチェックをおこないます。
		String nameOther = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_NAME_OTHER);
		if (!HimStringCheckUtil.checkEmpty(nameOther)) {
			if (!HimStringCheckUtil.checkMaxLength(nameOther, HimConstant.LONG_NAME_OTHER)) {
				// 100文字以内であること(氏名)
				ie.addErrMsgList(HimConstant.PROPERTY_NAME_OTHER, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_NAME_OTHER, String.valueOf(HimConstant.LONG_NAME_OTHER));
			} else if (!HimStringCheckUtil.checkZenkaku(nameOther)) {
				// 全角チェック
				ie.addErrMsgList(HimConstant.PROPERTY_NAME_OTHER, HimMsgConstant.MSG_HIM_W0094,
					strFileLineNumber, HimConstant.ITEM_NAME_OTHER);
			}
		}

		// 氏名(その他)(カナ)のチェックをおこないます。
		String nameKanaOther = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_NAME_KANA_OTHER);
		if (!HimStringCheckUtil.checkEmpty(nameKanaOther)) {
			if (!HimStringCheckUtil.checkMaxLength(nameKanaOther, HimConstant.LONG_NAME_OTHER)) {
				// 100文字以内であること(氏名かな)
				ie.addErrMsgList(HimConstant.PROPERTY_NAME_KANA_OTHER, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_NAME_KANA_OTHER, String.valueOf(HimConstant.LONG_NAME_OTHER));
			} else if (!HimStringCheckUtil.checkHankaku(nameKanaOther)) {
				// 半角カナまたは半角英字、半角記号、半角空白
				ie.addErrMsgList(HimConstant.PROPERTY_NAME_KANA_OTHER, HimMsgConstant.MSG_HIM_W0135,
					strFileLineNumber, HimConstant.ITEM_NAME_KANA_OTHER);
			}
		}

		// 生年月日のチェックをおこないます。
		String birthDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_BIRTHDATE);
		if (!HimStringCheckUtil.checkEmpty(birthDate)) {
			try {
				// 日付型に変換
				sdf.parse(birthDate);
			} catch (ParseException pe) {
				ie.addErrMsgList(HimConstant.PROPERTY_BIRTHDATE, CmnMsgConstant.MSG_CMN_W2131,
						strFileLineNumber, HimConstant.ITEM_BIRTHDATE);
			}
		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_BIRTHDATE, CmnMsgConstant.MSG_CMN_W2124,
					strFileLineNumber, HimConstant.ITEM_BIRTHDATE);
		}

		// 性別1,性別2のチェックをおこないます。
		String sex1 = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_SEX1);
		String sex2 = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_SEX2);
		if (!HimStringCheckUtil.checkEmpty(sex1)) {
			if (!HimCodeCheckUtil.checkSex1Definition(sex1)) {
				// 性別1に性別(1～3)が設定されていること。
				ie.addErrMsgList(HimConstant.PROPERTY_SEX, CmnMsgConstant.MSG_CMN_W2125, strFileLineNumber,
					HimConstant.ITEM_SEX1);

			} else if (sex1.equals(HimConstant.CONFIRM_DISP_IDENTITY_EXIST)) {
				// 性別1が3の場合、性別2が設定されていること。
				if (!HimStringCheckUtil.checkEmpty(sex2)) {
					if (!HimCodeCheckUtil.checkSex2Definition(sex2)) {
						// 性別2に性別(1～2)が設定されていること。
						ie.addErrMsgList(HimConstant.PROPERTY_SEX2, CmnMsgConstant.MSG_CMN_W2125, strFileLineNumber,
							HimConstant.ITEM_SEX2);
					}
				} else {
					ie.addErrMsgList(HimConstant.PROPERTY_SEX2, CmnMsgConstant.MSG_CMN_W2124,
							strFileLineNumber, HimConstant.ITEM_SEX2);
				}
			}
		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_SEX, CmnMsgConstant.MSG_CMN_W2124,
					strFileLineNumber, HimConstant.ITEM_SEX1);
		}

		// 住所のチェックをおこないます。
		String address = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_ADDRESS);
		// 250文字以内であること(住所)
		if (!HimStringCheckUtil.checkEmpty(address)
				&& !HimStringCheckUtil.checkMaxLength(address, HimConstant.LONG_ADDRESS)) {
			ie.addErrMsgList(HimConstant.PROPERTY_ADDRESS, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_ADDRESS, String.valueOf(HimConstant.LONG_ADDRESS));
		}

		// 郵便番号のチェックをおこないます。
		String zip = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_ZIP);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(zip)) {
			if (!zip.matches(HimConstant.CHECK_ZIP)) {
				// 郵便番号のフォーマット
				ie.addErrMsgList(HimConstant.PROPERTY_ZIP, HimMsgConstant.MSG_HIM_W0098, strFileLineNumber);
			}
		}

		// 市町村コードのチェックをおこないます。
		String cityCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_CITY_CODE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(cityCode)) {
			if (!HimStringCheckUtil.checkNum(cityCode)) {
				// 文字種(半角数字)
				ie.addErrMsgList(HimConstant.PROPERTY_CITY_CODE, CmnMsgConstant.MSG_CMN_W2127,
						strFileLineNumber, HimConstant.ITEM_CITY_CODE);
			} else if (!HimStringCheckUtil.checkLength(cityCode, HimConstant.LENGTH_CITY_CODE)) {
				// 桁数(6文字)
				ie.addErrMsgList(HimConstant.PROPERTY_CITY_CODE, CmnMsgConstant.MSG_CMN_W2126,
						strFileLineNumber, HimConstant.ITEM_CITY_CODE, String.valueOf(HimConstant.LENGTH_CITY_CODE));
			} else if (!checkMuniCodeExists(cityCode, em , sib)) {
		        // 市町村コード存在チェック
		        // 市町村コードが存在しない場合
		        ie.addErrMsgList(HimConstant.PROPERTY_CITY_CODE, CmnMsgConstant.MSG_CMN_W2134,
		           strFileLineNumber, HimConstant.ITEM_CITY_CODE, String.valueOf(HimConstant.LENGTH_CITY_CODE));
			}
		}

		// アクセスグループコードのチェックをおこないます。
		String accessGroupCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_ACCESS_GROUP_CODE);
		if (!HimStringCheckUtil.checkEmpty(accessGroupCode)) {
			// アクセスグループコードが20文字以内に設定されていること
			if (!HimStringCheckUtil.checkMaxLength(accessGroupCode, HimConstant.LONG_ACCESSGROUPCODE)) {
				ie.addErrMsgList(HimConstant.PROPERTY_ACCESS_GROUP_CODE, CmnMsgConstant.MSG_CMN_W2129,
						strFileLineNumber, HimConstant.ITEM_ACCESS_GROUP_CODE, String.valueOf(HimConstant.LONG_ACCESSGROUPCODE));
			}
			// 半角英数字であること(アクセスグループコード)
			if (!HimStringCheckUtil.checkAlphaNum(accessGroupCode)) {
				ie.addErrMsgList(HimConstant.PROPERTY_ACCESS_GROUP_CODE, CmnMsgConstant.MSG_CMN_W2169,
						strFileLineNumber, HimConstant.ITEM_ACCESS_GROUP_CODE);
			}
		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			// 必須項目であること(アクセスグループコード)
			ie.addErrMsgList(HimConstant.PROPERTY_ACCESS_GROUP_CODE, CmnMsgConstant.MSG_CMN_W2124,
					strFileLineNumber, HimConstant.ITEM_ACCESS_GROUP_CODE);
		}

		// 身分
		String position = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_POSITION);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(position)) {
			if (!position.matches(HimConstant.CHECK_POSITION)) {
				// 数値(1,2)
				ie.addErrMsgList(HimConstant.PROPERTY_POSITION, CmnMsgConstant.MSG_CMN_W2125,
						strFileLineNumber, HimConstant.ITEM_POSITION);
			}
		} else if (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE) != null &&
				dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE).startsWith("K")) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_POSITION, HimMsgConstant.MSG_HIM_W0150,
					strFileLineNumber, HimConstant.ITEM_POSITION);
		}

		// 加入者区分コード
		String segmentOfSubscriber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_SEGMENT_OF_SUBSCRIBER);
		if (!HimStringCheckUtil.checkEmpty(segmentOfSubscriber)) {
			if (!segmentOfSubscriber.matches(HimConstant.CHECK_SEGMENT_OF_SUBSCRIBER)) {
				// 数値(0,1,2)
				ie.addErrMsgList(HimConstant.PROPERTY_SEGMENT_OF_SUBSCRIBER, CmnMsgConstant.MSG_CMN_W2125,
						strFileLineNumber, HimConstant.ITEM_SEGMENT_OF_SUBSCRIBER);
			}
		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_SEGMENT_OF_SUBSCRIBER, CmnMsgConstant.MSG_CMN_W2124,
					strFileLineNumber, HimConstant.ITEM_SEGMENT_OF_SUBSCRIBER);
		}

		// 世帯識別番号
		String householdIdentificationNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_HOUSEHOLD_IDENTIFICATION_NUMBER);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(householdIdentificationNumber)) {
			if (!HimStringCheckUtil.checkAlphaNum(householdIdentificationNumber)) {
				// 文字種(半角英数字)
				ie.addErrMsgList(HimConstant.PROPERTY_HOUSEHOLD_IDENTIFICATION_NUMBER, CmnMsgConstant.MSG_CMN_W2169,
						strFileLineNumber, HimConstant.ITEM_HOUSEHOLD_IDENTIFICATION_NUMBER);
			} else if (!HimStringCheckUtil.checkMaxLength(householdIdentificationNumber, HimConstant.LENGTH_HOUSEHOLD_IDENTIFICATION_NUMBER)) {
				// 文字数(最大20文字)
				ie.addErrMsgList(HimConstant.PROPERTY_HOUSEHOLD_IDENTIFICATION_NUMBER, CmnMsgConstant.MSG_CMN_W2129,
						strFileLineNumber, HimConstant.ITEM_HOUSEHOLD_IDENTIFICATION_NUMBER, String.valueOf(HimConstant.LENGTH_HOUSEHOLD_IDENTIFICATION_NUMBER));
			}
		}

		// 加入者区分の変更、加入者基本情報レコード以外の情報チェック
		if(!checkSegmentChange(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE), dataList
				.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURED_BRANCH_NUMBER),dataList
				.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_SEGMENT_OF_SUBSCRIBER),conExist)) {
			// 加入者区分の変更
			ie.addErrMsgList(HimConstant.PROPERTY_SEGMENT_OF_SUBSCRIBER, HimMsgConstant.MSG_HIM_W0110,
					strFileLineNumber);
		}
		// 登録の場合
		if(HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {

			if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PERSONAL_NUMBER))) {

				// 個人番号存在チェック。
        		HimSubsLogic subslogic = new HimSubsLogic(em, sib);
        		// 保険者コードを取り出す
        		String insurerCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE);
                // 個人番号を取り出す
                String personalNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PERSONAL_NUMBER);
                // 被保険者枝番を取り出す
                String insuredBranchNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURED_BRANCH_NUMBER);
                // 重複先の被保険者枝番を取得
                String dupeInsuredBranchNumber =
                    subslogic.isDuplicatePersonalNumberInsuredBranchNumber(insurerCode, personalNumber, insuredBranchNumber);
        		if (!HimStringCheckUtil.checkEmpty(dupeInsuredBranchNumber)) {
                    // 個人番号が重複した被保険者枝番が取得できているのでエラー
					ie.addErrMsgList(HimConstant.PROPERTY_PERSONAL_NUMBER, HimMsgConstant.MSG_HIM_W0106,
					   strFileLineNumber, HimConstant.ITEM_PERSONAL_NUMBER, dupeInsuredBranchNumber);
        		}
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者基本情報変更履歴レコード存在チェック。</dd>
	 * <dd>メソッド説明：加入者基本情報変更履歴レコードを存在チェックします。</dd>
	 * <dd>備考：PR_HIM_01_202</dd>
	 * </dl>
	 * @param dataList
	 * @param checkQualDatas
	 * @param lineNumber
	 * @param strFileLineNumber
	 * @param ie
	 * @throws MilException
	 * @throws ParseException
	 */
	private static void checkSubscriberHistoryList(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, EntityManager em,  SystemInfoBean sib, SimpleDateFormat sdf) throws MilException {

		// 変更年月日のチェックをおこないます。
		String changeDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_CHANGE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(changeDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_CHANGE_DATE, HimMsgConstant.MSG_HIM_W0099,
						HimConstant.ITEM_SUBSCRIBER_DATA_HISTORY,strFileLineNumber, HimConstant.ITEM_CHANGE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(changeDate)) {
			// 日付妥当性チェック
			if(!checkDateFormat(changeDate, sdf)) {
			    // 日付として妥当ではない場合
			    setDateFormatError(ie, HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_CHANGE_DATE, HimConstant.ITEM_CHANGE_DATE);
		    }
	    }

		// 氏名(券面記載)のチェックをおこないます。
		String name = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_NAME);
		// 氏名入力チェック
		// 100文字以内であること(氏名)
		if(!HimStringCheckUtil.checkEmpty(name)) {
			if (!HimStringCheckUtil.checkMaxLength(name, HimConstant.LONG_NAME)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_NAME, CmnMsgConstant.MSG_CMN_W2129,
						strFileLineNumber, HimConstant.ITEM_HISTORY_NAME, String.valueOf(HimConstant.LONG_NAME));
			} else if (!HimStringCheckUtil.checkZenkaku(name)) {
				// 全角チェック
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_NAME, HimMsgConstant.MSG_HIM_W0094,
						strFileLineNumber, HimConstant.ITEM_HISTORY_NAME);
			}
		}

		// 氏名(券面記載)(カナ)のチェックをおこないます。
		String nameKana = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_NAME_KANA);
		if (!HimStringCheckUtil.checkEmpty(nameKana)) {
			// 氏名かな入力チェック
			if (!HimStringCheckUtil.checkMaxLength(nameKana, HimConstant.LONG_NAME)) {
				// 100文字以内であること(氏名かな)
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_NAME_KANA, CmnMsgConstant.MSG_CMN_W2129,
						strFileLineNumber, HimConstant.ITEM_HISTORY_NAME_KANA, String.valueOf(HimConstant.LONG_NAME));
			} else if (!HimStringCheckUtil.checkHankaku(nameKana)) {
				// 半角カナまたは半角英字、半角記号、半角空白
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_NAME_KANA, HimMsgConstant.MSG_HIM_W0135,
					strFileLineNumber, HimConstant.ITEM_HISTORY_NAME_KANA);
			}
		}

		// 氏名(その他)のチェックをおこないます。
		String nameOther = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_NAME_OTHER);
		// 氏名入力チェック
		// 100文字以内であること(氏名)
		if(!HimStringCheckUtil.checkEmpty(nameOther)) {
			if (!HimStringCheckUtil.checkMaxLength(nameOther, HimConstant.LONG_NAME_OTHER)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_NAME_OTHER, CmnMsgConstant.MSG_CMN_W2129,
						strFileLineNumber, HimConstant.ITEM_HISTORY_NAME_OTHER, String.valueOf(HimConstant.LONG_NAME_OTHER));
			} else if (!HimStringCheckUtil.checkZenkaku(nameOther)) {
				// 全角チェック
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_NAME_OTHER, HimMsgConstant.MSG_HIM_W0094,
						strFileLineNumber, HimConstant.ITEM_HISTORY_NAME_OTHER);
			}
		}

		// 氏名(その他)(カナ)のチェックをおこないます。
		String nameKanaOther = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_NAME_KANA_OTHER);
		if (!HimStringCheckUtil.checkEmpty(nameKanaOther)) {
			// 氏名かな入力チェック
			if (!HimStringCheckUtil.checkMaxLength(nameKanaOther, HimConstant.LONG_NAME_OTHER)) {
				// 100文字以内であること(氏名かな)
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_NAME_KANA_OTHER, CmnMsgConstant.MSG_CMN_W2129,
						strFileLineNumber, HimConstant.ITEM_HISTORY_NAME_KANA_OTHER, String.valueOf(HimConstant.LONG_NAME_OTHER));
			} else if (!HimStringCheckUtil.checkHankaku(nameKanaOther)) {
				// 半角カナまたは半角英字、半角記号、半角空白
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_NAME_KANA_OTHER, HimMsgConstant.MSG_HIM_W0135,
					strFileLineNumber, HimConstant.ITEM_HISTORY_NAME_KANA_OTHER);
			}
		}

		// 生年月日のチェックをおこないます。
		String birthDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_BIRTHDATE);
		if (!HimStringCheckUtil.checkEmpty(birthDate)) {
			try {
				// 日付型に変換
				sdf.parse(birthDate);
			} catch (ParseException pe) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_BIRTHDATE, CmnMsgConstant.MSG_CMN_W2131,
						strFileLineNumber, HimConstant.ITEM_HISTORY_BIRTHDATE);
			}
		}

		// 性別1,性別2のチェックをおこないます。
		String sex1 = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_SEX1);
		String sex2 = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_SEX2);
		if (!HimStringCheckUtil.checkEmpty(sex1)) {
			if (!HimCodeCheckUtil.checkSex1Definition(sex1)) {
				// 性別1に性別(1～3)が設定されていること。
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_SEX, CmnMsgConstant.MSG_CMN_W2125, strFileLineNumber,
					HimConstant.ITEM_HISTORY_SEX1);
			}
		}
		if (!HimStringCheckUtil.checkEmpty(sex2)) {
			if (!HimCodeCheckUtil.checkSex2Definition(sex2)) {
				// 性別2に性別(1～2)が設定されていること。
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_SEX2, CmnMsgConstant.MSG_CMN_W2125, strFileLineNumber,
					HimConstant.ITEM_HISTORY_SEX2);
			}
		}

		// 住所のチェックをおこないます。
		String address = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_ADDRESS);
		// 250文字以内であること(住所)
		if (!HimStringCheckUtil.checkEmpty(address)
				&& !HimStringCheckUtil.checkMaxLength(address, HimConstant.LONG_ADDRESS)) {
			ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_ADDRESS, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_HISTORY_ADDRESS, String.valueOf(HimConstant.LONG_ADDRESS));
		}

		// 郵便番号のチェックをおこないます。
		String zip = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_ZIP);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(zip)) {
			if (!zip.matches(HimConstant.CHECK_ZIP)) {
				// 郵便番号のフォーマット
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_ZIP, HimMsgConstant.MSG_HIM_W0098,
						strFileLineNumber, HimConstant.ITEM_HISTORY_ZIP);
			}
		}

		// 市町村コードのチェックをおこないます。
		String cityCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_CITY_CODE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(cityCode)) {
			if (!HimStringCheckUtil.checkNum(cityCode)) {
				// 文字種(半角数字)
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_CITY_CODE, CmnMsgConstant.MSG_CMN_W2127,
						strFileLineNumber, HimConstant.ITEM_HISTORY_CITY_CODE);
			} else if (!HimStringCheckUtil.checkLength(cityCode, HimConstant.LENGTH_CITY_CODE)) {
				// 桁数(6文字)
				ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_CITY_CODE, CmnMsgConstant.MSG_CMN_W2126,
						strFileLineNumber, HimConstant.ITEM_HISTORY_CITY_CODE, String.valueOf(HimConstant.LENGTH_CITY_CODE));
			} else if (!checkMuniCodeExists(cityCode, em , sib)) {
		        // 市町村コード存在チェック
		        // 市町村コードが存在しない場合
		        ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY_CITY_CODE, CmnMsgConstant.MSG_CMN_W2134,
		                strFileLineNumber, HimConstant.ITEM_HISTORY_CITY_CODE, String.valueOf(HimConstant.LENGTH_CITY_CODE));
			}
		}

		// 「変更年月日」以外の何れか1項目以上を設定する。
		if (HimStringCheckUtil.checkEmpty(name) && HimStringCheckUtil.checkEmpty(nameKana) && HimStringCheckUtil.checkEmpty(nameOther) &&
				HimStringCheckUtil.checkEmpty(nameKanaOther) && HimStringCheckUtil.checkEmpty(birthDate) && HimStringCheckUtil.checkEmpty(sex1) &&
				HimStringCheckUtil.checkEmpty(sex2) && HimStringCheckUtil.checkEmpty(address) && HimStringCheckUtil.checkEmpty(zip) &&
				HimStringCheckUtil.checkEmpty(cityCode)) {
			// エラーメッセージ
			ie.addErrMsgList(HimConstant.PROPERTY_HISTORY_INFO, HimMsgConstant.MSG_HIM_W0071);
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者資格情報レコード存在チェック。</dd>
	 * <dd>メソッド説明：加入者資格情報レコードを存在チェックします。</dd>
	 * <dd>備考：PR_HIM_01_202</dd>
	 * </dl>
	 * @param dataList
	 * @param checkQualDatas
	 * @param lineNumber
	 * @param strFileLineNumber
	 * @param ie
	 * @throws MilException
	 * @throws ParseException
	 */
	private static void checkQualificationList(List<String> dataList, List<String> checkQualDatas, int lineNumber,
			String strFileLineNumber, MilInputErrorException ie, SimpleDateFormat sdf) throws MilException, ParseException {

		// 保険者番号のチェックをおこないます。
		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURER_NUMBER))) {
			// 半角数字であること(保険者番号)
			if (!HimStringCheckUtil.checkNum(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURER_NUMBER))) {
				ie.addErrMsgList(HimConstant.PROPERTY_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_INSURER_NUMBER);
			}

			// 8文字であること(保険者番号)
			if (!HimStringCheckUtil.checkLength(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURER_NUMBER), HimConstant.LONG_INSURERCODE)) {
				ie.addErrMsgList(HimConstant.PROPERTY_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_INSURER_NUMBER, String.valueOf(HimConstant.LONG_INSURERCODE));
			}
		} else if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_INSURER_NUMBER, HimMsgConstant.MSG_HIM_W0034,
				strFileLineNumber, HimConstant.ITEM_INSURER_NUMBER);
		}

		// 被保険者証記号のチェックをおこないます。
		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_PROOF_CODE))) {
			// 20文字以内であること(被保険者証記号)
			if (!CheckUtil.isMaxLength(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_PROOF_CODE), HimConstant.LONG_INSURED_PROOF_CODE)) {
				ie.addErrMsgList(HimConstant.PROPERTY_INSURED_PROOF_CODE, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_INSURED_PROOF_CODE, String.valueOf(HimConstant.LONG_INSURED_PROOF_CODE));
			}
		}

		// 被保険者証番号のチェックをおこないます。
		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_PROOF_NUMBER))) {
			// 20文字以内であること(被保険者証番号)
			if (!CheckUtil.isMaxLength(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_PROOF_NUMBER), HimConstant.LONG_INSURED_PROOF_NUMBER)) {
				ie.addErrMsgList(HimConstant.PROPERTY_INSURED_PROOF_NUMBER, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_INSURED_PROOF_NUMBER, String.valueOf(HimConstant.LONG_INSURED_PROOF_NUMBER));
			}
		} else {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_INSURED_PROOF_NUMBER, HimMsgConstant.MSG_HIM_W0034,
				strFileLineNumber, HimConstant.ITEM_INSURED_PROOF_NUMBER);
		}

		// 被保険者証枝番のチェックをおこないます。
		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_BRANCH_NUMBER))) {
			// 2文字であること(被保険者証枝番)
			if (!HimStringCheckUtil.checkLength(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_BRANCH_NUMBER), HimConstant.LENGTH_BRANCH_NAME)) {
				ie.addErrMsgList(HimConstant.PROPERTY_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_BRANCH_NAME, String.valueOf(HimConstant.LONG_INSUREDBRANCHNUMBER));
			}
			// 半角数字であること(被保険者証枝番)
			if (!HimStringCheckUtil.checkNum(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_BRANCH_NUMBER))) {
				ie.addErrMsgList(HimConstant.PROPERTY_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_BRANCH_NAME);
			}
		}
		// 後期高齢者医療以外の場合必須
		if (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE) != null && !dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE).startsWith("C")) {
			if(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE) != null) {
				Date disqualificationDate = sdf.parse(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE));
				Date target = sdf.parse(HimConstant.SYSTEM_ENFORCEMENT_DATE);
				if (target.compareTo(disqualificationDate) <= 0 && HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_BRANCH_NUMBER))) {
					ie.addErrMsgList(HimConstant.PROPERTY_BRANCH_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_SUBSCRIBER_QUALIFICATION,
							strFileLineNumber, HimConstant.ITEM_BRANCH_NAME);
				}
			}else {
				if (HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_BRANCH_NUMBER))) {
					ie.addErrMsgList(HimConstant.PROPERTY_BRANCH_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_SUBSCRIBER_QUALIFICATION,
							strFileLineNumber, HimConstant.ITEM_BRANCH_NAME);
				}
			}
		}

		// 資格取得年月日のチェックをおこないます。
		Date qualDate = checkQualificationDate(dataList, strFileLineNumber, ie, sdf);

		// 資格喪失年月日のチェックをおこないます。
		Date disqualDate = checkDisqualificationDate(dataList, strFileLineNumber, ie, sdf);

		// 資格取得年月日に入力した日付よりも過去の日付が入力されていないこと(資格喪失年月日)
		if (qualDate != null && disqualDate != null
				&& !HimDateCheckUtil.checkPastDate(qualDate, disqualDate)) {
			ie.addErrMsgList(HimConstant.PROPERTY_DISQUALIFICATION2_DATE, CmnMsgConstant.MSG_CMN_W2158,
				strFileLineNumber, HimConstant.ITEM_DISQUALIFICATION2_DATE,
				HimConstant.ITEM_QUALIFICATION2_DATE);
		}

		// 資格喪失事由のチェックをおこないます。
		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE)) &&
				HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_REASON))) {
			// 必須チェック(資格喪失年月日が入力されている場合)
			ie.addErrMsgList(HimConstant.PROPERTY_M_QUALIFICATION_DISQUALIFICATION_REASON, CmnMsgConstant.MSG_CMN_W2124,
					strFileLineNumber, HimConstant.ITEM_DISQUALIFICATION_REASON);
		} else if (HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE)) &&
				!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_REASON))) {
			// 設定できない
			ie.addErrMsgList(HimConstant.PROPERTY_M_QUALIFICATION_DISQUALIFICATION_REASON, HimMsgConstant.MSG_HIM_W0136,
					strFileLineNumber, HimConstant.ITEM_DISQUALIFICATION_REASON);
		}
		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_REASON))) {
			if (!dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_REASON).matches(HimConstant.CHECK_DISQUALIFICATION_REASON)) {
				// 数値(01,02,99)
				ie.addErrMsgList(HimConstant.PROPERTY_M_QUALIFICATION_DISQUALIFICATION_REASON, CmnMsgConstant.MSG_CMN_W2125,
					strFileLineNumber, HimConstant.ITEM_DISQUALIFICATION_REASON);
			}
		}

		// 本人・家族の別
		if (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE) != null &&
				dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE).startsWith("B") &&
				CheckUtil.isEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_HOUSEHOLDER_DIVISION))) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_M_QUALIFICATION_HOUSEHOLDER_DIVISION, HimMsgConstant.MSG_HIM_W0151,
					strFileLineNumber, HimConstant.ITEM_HOUSEHOLDER_DIVISION);
		}
		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_HOUSEHOLDER_DIVISION))) {
			if (!dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_HOUSEHOLDER_DIVISION).matches(HimConstant.CHECK_HOUSEHOLDER_DIVISION)) {
				// 数値(1,2)
				ie.addErrMsgList(HimConstant.PROPERTY_M_QUALIFICATION_DISQUALIFICATION_REASON, CmnMsgConstant.MSG_CMN_W2125,
					strFileLineNumber, HimConstant.ITEM_HOUSEHOLDER_DIVISION);
			}
		}

		// 被保険者氏名のチェックをおこないます。
		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_NAME))) {
			// 100文字以内であること(被保険者氏名)
			if (!CheckUtil.isMaxLength(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_NAME), HimConstant.LENGTH_INSURED_NAME)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_QUALIFICATION_INSURED_NAME, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_INSURED_NAME, String.valueOf(HimConstant.LENGTH_INSURED_NAME));
			}
			// 全角であること(被保険者氏名)
			if (!HimStringCheckUtil.checkZenkaku(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_NAME))) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_QUALIFICATION_INSURED_NAME, CmnMsgConstant.MSG_CMN_W2228,
					strFileLineNumber, HimConstant.ITEM_INSURED_NAME);
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：被保険者証等情報レコード存在チェック。</dd>
	 * <dd>メソッド説明：被保険者証等情報レコードを存在チェックします。</dd>
	 * <dd>備考：PR_HIM_01_202</dd>
	 * </dl>
	 * @param dataList
	 * @param checkQualDatas
	 * @param lineNumber
	 * @param strFileLineNumber
	 * @param ie
	 * @throws MilException
	 * @throws ParseException
	 */
	private static void checkInsuredProofInfoInputBeanList(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, String qualDisqualDate, boolean qualExist, SimpleDateFormat sdf) throws MilException, ParseException {

        // 被保険者証有効開始年月日入力チェックOKフラグ
        boolean availableDateOK = false;
        // 被保険者証有効終了年月日入力チェックOKフラグ
		boolean invalidDateOK = false;

		// 被保険者証区分
		String insuredProofDivisionNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_INSURED_PROOF_DIVISION);
		if (!HimStringCheckUtil.checkEmpty(insuredProofDivisionNumber)) {
			if (!insuredProofDivisionNumber.matches(HimConstant.CHECK_INSURED_PROOF_DIVISION)) {
				// 数値(01～07)
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INSURED_PROOF_DIVISION, CmnMsgConstant.MSG_CMN_W2125,
						strFileLineNumber, HimConstant.ITEM_INSURED_PROOF_DIVISION);
			}
		} else if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INSURED_PROOF_DIVISION, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_INSURED_PROOF_DIVISION);
		}

		// 保険者番号（証）
		String insurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(insurerNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_CARD_INSURER_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(insurerNumber)) {
			if (!HimStringCheckUtil.checkNum(insurerNumber)) {
				// 半角数字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_CARD_INSURER_NUMBER);
			} else if (!HimStringCheckUtil.checkLength(insurerNumber, HimConstant.LENGTH_CARD_INSURER_NUMBER)) {
				// 8文字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_CARD_INSURER_NUMBER, String.valueOf(HimConstant.LENGTH_CARD_INSURER_NUMBER));
			}
		}

		// 被保険者証記号（証）
		String insuredProofCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(insuredProofCode)) {
			// 20文字以内であること。
			if (!HimStringCheckUtil.checkMaxLength(insuredProofCode, HimConstant.LENGTH_CARD_INSURED_PROOF_SYMBOL)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_CARD_INSURED_PROOF_SYMBOL, String.valueOf(HimConstant.LENGTH_CARD_INSURED_PROOF_SYMBOL));
			}
		}

		// 被保険者証番号（証）
		String insuredProofNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(insuredProofNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_CARD_INSURED_PROOF_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(insuredProofNumber)) {
			// 20文字以内であること。
			if (!HimStringCheckUtil.checkMaxLength(insuredProofNumber, HimConstant.LENGTH_CARD_INSURED_PROOF_NUMBER)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_CARD_INSURED_PROOF_NUMBER, String.valueOf(HimConstant.LENGTH_CARD_INSURED_PROOF_NUMBER));
			}
		}

		// 被保険者証枝番（証）
		String branchNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER);
		// 後期高齢者医療以外の場合必須
		if (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE) != null && dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE).startsWith("B")) {
			if (HimStringCheckUtil.checkEmpty(branchNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER, HimMsgConstant.MSG_HIM_W0145, HimConstant.ITEM_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_CARD_BRANCH_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(branchNumber)) {
			if (!HimStringCheckUtil.checkNum(branchNumber)) {
				// 文字種(半角数字)
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_CARD_BRANCH_NUMBER);
			} else if (!HimStringCheckUtil.checkLength(branchNumber, HimConstant.LENGTH_CARD_BRANCH_NUMBER)) {
				// 桁数(2文字)
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_CARD_BRANCH_NUMBER, String.valueOf(HimConstant.LENGTH_CARD_BRANCH_NUMBER));
			}
		}

		// 被保険者証交付年月日
		String issuanceDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_ISSUANCE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(issuanceDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_ISSUANCE_DATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_ISSUANCE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(issuanceDate)) {
			// 日付妥当性チェック
			if (!checkDateFormat(issuanceDate, sdf)) {
    			// 日付として妥当ではない場合
			    setDateFormatError(ie, HimConstant.PROPERTY_M_INSURED_PROOF_INFO_ISSUANCE_DATE, strFileLineNumber, HimConstant.ITEM_ISSUANCE_DATE);
		    }
		}

		// 被保険者証有効開始年月日
		String availableDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_AVAILABLE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(availableDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_AVAILABLE_DATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_AVAILABLE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(availableDate)) {
			// 日付妥当性チェック
			availableDateOK = checkDateFormat(availableDate, sdf);
			if (!availableDateOK) {
    			// 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_INSURED_PROOF_INFO_AVAILABLE_DATE, strFileLineNumber, HimConstant.ITEM_AVAILABLE_DATE);
		    }
		}

		// 被保険者証有効終了年月日
		String invalidDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_INVALID_DATE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(invalidDate)) {
			// 日付妥当性チェック
			invalidDateOK = checkDateFormat(invalidDate, sdf);
			if (!invalidDateOK) {
    			// 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INVALID_DATE, strFileLineNumber, HimConstant.ITEM_INVALID_DATE);
			}
		}
		// 処理種別12：加入者情報のレコード種別単位更新の場合
		if (HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			if(qualExist) {
				if (!"".equals(qualDisqualDate) && qualDisqualDate != null) {
					// 資格喪失状態
					if (HimStringCheckUtil.checkEmpty(invalidDate)) {
						// 必須
						ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
								strFileLineNumber, HimConstant.ITEM_INVALID_DATE);
					} else if (invalidDateOK && invalidDate.compareTo(qualDisqualDate) > 0) {
					    // 妥当な日付が設定されている場合かつ、
						// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
						ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
								strFileLineNumber, HimConstant.ITEM_INVALID_DATE);
					}
				}
			} else {
				String lastDisqualDate = checkDisqualifiedQual(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE), dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PERSONAL_NUMBER), sdf);
				if (!"".equals(lastDisqualDate)) {
					// 資格喪失状態
					if (HimStringCheckUtil.checkEmpty(invalidDate)) {
						// 必須
						ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
								strFileLineNumber, HimConstant.ITEM_INVALID_DATE);
					} else if (invalidDateOK && invalidDate.compareTo(lastDisqualDate) > 0) {
					    // 妥当な日付が設定されている場合かつ、
						// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
						ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
								strFileLineNumber, HimConstant.ITEM_INVALID_DATE);
					}
				}
			}
		}
		// 処理種別11：加入者情報の登録または処理種別13:加入者の全体更新の場合
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))
				|| HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			if (!"".equals(qualDisqualDate) && qualDisqualDate != null) {
				// 資格喪失状態
				if (HimStringCheckUtil.checkEmpty(invalidDate)) {
					// 必須
					ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
							strFileLineNumber, HimConstant.ITEM_INVALID_DATE);
				} else if (invalidDateOK && invalidDate.compareTo(qualDisqualDate) > 0) {
				    // 妥当な日付が設定されている場合かつ、
					// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
					ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
							strFileLineNumber, HimConstant.ITEM_INVALID_DATE);
				}
			}
		}

		// 被保険者証有効開始年月日、被保険者証有効終了年月日の両方が設定され、入力チェックOKの場合のみ、過去日チェックを行う。
		if (availableDateOK && invalidDateOK) {
			if (!HimDateCheckUtil.checkPastDate(sdf.parse(availableDate), sdf.parse(invalidDate))) {
				// 被保険者証有効開始年月日が被保険者証有効終了年月日より過去日であること。
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_INVALID_DATE,
						CmnMsgConstant.MSG_CMN_W2158, strFileLineNumber, HimConstant.ITEM_INVALID_DATE,
						HimConstant.ITEM_AVAILABLE_DATE);
			}
		}

		// 被保険者証一部負担金割合
		String copaymentRatio = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_COPAYMENT_RATIO);
		// 後期高齢者医療の場合必須
		if (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE) != null && dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE).startsWith("C")) {
			if (HimStringCheckUtil.checkEmpty(copaymentRatio)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_COPAYMENT_RATIO, HimMsgConstant.MSG_HIM_W0144, HimConstant.ITEM_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_COPAYMENT_RATIO);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(copaymentRatio)) {
			if (!HimStringCheckUtil.checkNum(copaymentRatio)) {
				// 文字種(半角数字)
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_COPAYMENT_RATIO, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_COPAYMENT_RATIO);
			} else if (!HimStringCheckUtil.checkMaxLength(copaymentRatio, HimConstant.LENGTH_COPAYMENT_RATIO)) {
				// 桁数(3文字以内)
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_COPAYMENT_RATIO, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_COPAYMENT_RATIO, String.valueOf(HimConstant.LENGTH_COPAYMENT_RATIO));
			} else if (Integer.valueOf(copaymentRatio) > 100) {
				// 100以内
				ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_COPAYMENT_RATIO, HimMsgConstant.MSG_HIM_W0105,
					strFileLineNumber, HimConstant.ITEM_COPAYMENT_RATIO, "100");
			}
		}

		// 被保険者証回収年月日
		String collectDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_COLLECT_DATE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(collectDate)) {
			// 日付妥当性チェック
			if (!checkDateFormat(collectDate, sdf)) {
    			// 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_INSURED_PROOF_INFO_COLLECT_DATE, strFileLineNumber, HimConstant.ITEM_COLLECT_DATE);
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：高齢受給者証情報レコード存在チェック。</dd>
	 * <dd>メソッド説明：高齢受給者証情報レコードを存在チェックします。</dd>
	 * <dd>備考：PR_HIM_01_202</dd>
	 * </dl>
	 * @param dataList
	 * @param checkQualDatas
	 * @param lineNumber
	 * @param strFileLineNumber
	 * @param ie
	 * @throws MilException
	 * @throws ParseException
	 */
	private static void checkElderlyInsuredProofInfoList(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, String qualDisqualDate, boolean qualExist, SimpleDateFormat sdf) throws MilException, ParseException {

        // 高齢受給者証有効開始年月日チェックOKフラグ
        boolean elderlyAvailableDateOK = false;
        // 高齢受給者証有効終了年月日チェックOKフラグ
        boolean elderlyInvalidDateOK = false;

		// 保険者番号（高齢受給者証）
		String insurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(insurerNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_INSURER_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(insurerNumber)) {
			if (!HimStringCheckUtil.checkNum(insurerNumber)) {
				// 半角数字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_INSURER_NUMBER);
			} else if (!HimStringCheckUtil.checkLength(insurerNumber, HimConstant.LENGTH_ELDERLY_CARD_INSURER_NUMBER)) {
				// 8文字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_INSURER_NUMBER, String.valueOf(HimConstant.LENGTH_ELDERLY_CARD_INSURER_NUMBER));
			}
		}

		// 被保険者証記号（高齢受給者証）
		String insuredProofSymbol = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL);
		// 設定なしの場合、チェックしない。
		if (HimStringCheckUtil.checkEmpty(insuredProofSymbol)) {
			// 20文字以内であること。
			if (!HimStringCheckUtil.checkMaxLength(insuredProofSymbol, HimConstant.LENGTH_ELDERLY_CARD_INSURED_PROOF_SYMBOL)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_INSURED_PROOF_SYMBOL, String.valueOf(HimConstant.LENGTH_ELDERLY_CARD_INSURED_PROOF_SYMBOL));
			}
		}

		// 被保険者証番号（高齢受給者証）
		String cardInsuredProofNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(cardInsuredProofNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_INSURED_PROOF_NUMBER);
			}
		}
		if (!HimStringCheckUtil.checkMaxLength(cardInsuredProofNumber, HimConstant.LENGTH_ELDERLY_CARD_INSURED_PROOF_NUMBER)){
			// 20文字以内であること。
			ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_INSURED_PROOF_NUMBER, String.valueOf(HimConstant.LENGTH_ELDERLY_CARD_INSURED_PROOF_NUMBER));
		}

		// 被保険者証枝番（高齢受給者証）
		String cardBranchNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER);
		// 後期高齢者医療以外の場合必須
		if (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE) != null && dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE).startsWith("B")) {
			if (HimStringCheckUtil.checkEmpty(cardBranchNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER, HimMsgConstant.MSG_HIM_W0145, HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_BRANCH_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(cardBranchNumber)) {
			if  (!HimStringCheckUtil.checkNum(cardBranchNumber)) {
				// 半角数字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_BRANCH_NUMBER);
			} else if (!HimStringCheckUtil.checkLength(cardBranchNumber, HimConstant.LENGTH_ELDERLY_CARD_BRANCH_NUMBER)) {
				// 2文字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_BRANCH_NUMBER, String.valueOf(HimConstant.LENGTH_ELDERLY_CARD_BRANCH_NUMBER));
			}
		}

		// 高齢受給者証交付年月日
		String elderlyIssuanceDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_ISSUANCE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(elderlyIssuanceDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_ISSUANCE_DATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_ISSUANCE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(elderlyIssuanceDate)) {
			if (!checkDateFormat(elderlyIssuanceDate, sdf)) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_ISSUANCE_DATE, strFileLineNumber, HimConstant.ITEM_ELDERLY_ISSUANCE_DATE);
			}
		}

		// 高齢受給者証有効開始年月日
		String elderlyAvailableDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_AVAILABLE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(elderlyAvailableDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_AVAILABLE_DATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_AVAILABLE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(elderlyAvailableDate)) {
		    elderlyAvailableDateOK = checkDateFormat(elderlyAvailableDate, sdf);
			if (!elderlyAvailableDateOK) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_AVAILABLE_DATE, strFileLineNumber, HimConstant.ITEM_ELDERLY_AVAILABLE_DATE);
			}
		}

		// 高齢受給者証有効終了年月日
		String elderlyInvalidDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(elderlyInvalidDate)) {
		    elderlyInvalidDateOK = checkDateFormat(elderlyInvalidDate, sdf);
			if (!elderlyInvalidDateOK) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE, strFileLineNumber, HimConstant.ITEM_ELDERLY_INVALID_DATE);
			}
		}
		// 処理種別12：加入者情報のレコード種別単位更新の場合
		if (HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			if(qualExist) {
				if (!"".equals(qualDisqualDate) && qualDisqualDate != null) {
					// 資格喪失状態
					if (HimStringCheckUtil.checkEmpty(elderlyInvalidDate)) {
						// 必須
						ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
								strFileLineNumber, HimConstant.ITEM_ELDERLY_INVALID_DATE);
					} else if (elderlyInvalidDateOK && elderlyInvalidDate.compareTo(qualDisqualDate) > 0) {
					    // 妥当な日付が設定されている場合かつ、
						// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
						ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
								strFileLineNumber, HimConstant.ITEM_ELDERLY_INVALID_DATE);
					}
				}
			} else {
				String lastDisqualDate = checkDisqualifiedQual(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE), dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PERSONAL_NUMBER), sdf);
				if (!"".equals(lastDisqualDate)) {
					// 資格喪失状態
					if (HimStringCheckUtil.checkEmpty(elderlyInvalidDate)) {
						// 必須
						ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
								strFileLineNumber, HimConstant.ITEM_ELDERLY_INVALID_DATE);
					} else if (elderlyInvalidDateOK && elderlyInvalidDate.compareTo(lastDisqualDate) > 0) {
					    // 妥当な日付が設定されている場合かつ、
						// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
						ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
								strFileLineNumber, HimConstant.ITEM_ELDERLY_INVALID_DATE);
					}
				}
			}
		}
		// 処理種別11：加入者情報の登録または処理種別13:加入者の全体更新の場合
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))
				|| HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			if (!"".equals(qualDisqualDate) && qualDisqualDate != null) {
				// 資格喪失状態
				if (HimStringCheckUtil.checkEmpty(elderlyInvalidDate)) {
					// 必須
					ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
							strFileLineNumber, HimConstant.ITEM_ELDERLY_INVALID_DATE);
				} else if (elderlyInvalidDateOK && elderlyInvalidDate.compareTo(qualDisqualDate) > 0) {
				    // 妥当な日付が設定されている場合かつ、
					// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
					ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
							strFileLineNumber, HimConstant.ITEM_ELDERLY_INVALID_DATE);
				}
			}
		}

		// 高齢受給者証有効開始年月日、高齢受給者証有効終了年月日の両方が設定され、入力チェックOKの場合のみ、過去日チェックを行う。
		if (elderlyAvailableDateOK && elderlyInvalidDateOK) {
			if (!HimDateCheckUtil.checkPastDate(sdf.parse(elderlyAvailableDate), sdf.parse(elderlyInvalidDate))) {
				// 高齢受給者証有効開始年月日が高齢受給者証有効終了年月日より過去日であること。
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_INVALID_DATE,
						CmnMsgConstant.MSG_CMN_W2158, strFileLineNumber, HimConstant.ITEM_ELDERLY_INVALID_DATE,
						HimConstant.ITEM_ELDERLY_AVAILABLE_DATE);
			}
		}

		// 高齢受給者証一部負担金割合
		String elderlyCopaymentRatio = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_COPAYMENT_RATIO);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(elderlyCopaymentRatio)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_COPAYMENT_RATIO, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_ELDERLY_INSURED_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_ELDERLY_COPAYMENT_RATIO);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(elderlyCopaymentRatio)) {
			if (!HimStringCheckUtil.checkNum(elderlyCopaymentRatio)) {
				// 文字種(半角数字)
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_COPAYMENT_RATIO, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_COPAYMENT_RATIO);
			} else if (!HimStringCheckUtil.checkMaxLength(elderlyCopaymentRatio, HimConstant.LENGTH_COPAYMENT_RATIO)) {
				// 桁数(3文字以内)
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_COPAYMENT_RATIO, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_COPAYMENT_RATIO, String.valueOf(HimConstant.LENGTH_COPAYMENT_RATIO));
			} else if (Integer.valueOf(elderlyCopaymentRatio) > 100) {
				// 100以内
				ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_COPAYMENT_RATIO, HimMsgConstant.MSG_HIM_W0105,
					strFileLineNumber, HimConstant.ITEM_COPAYMENT_RATIO, "100");
			}
		}

		// 高齢者受給者証回収年月日
		String elderlyCollectDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_COLLECT_DATE);

		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(elderlyCollectDate)) {
			if (!checkDateFormat(elderlyCollectDate, sdf)) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_COLLECT_DATE, strFileLineNumber, HimConstant.ITEM_ELDERLY_COLLECT_DATE);
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：限度額適用認定証関連情報レコード存在チェック。</dd>
	 * <dd>メソッド説明：限度額適用認定証関連情報レコードを存在チェックします。</dd>
	 * <dd>備考：PR_HIM_01_202</dd>
	 * </dl>
	 * @param dataList
	 * @param checkQualDatas
	 * @param lineNumber
	 * @param strFileLineNumber
	 * @param ie
	 * @throws MilException
	 * @throws ParseException
	 */
	private static void checkLimitOfCopaymentInputBeanList(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, String qualDisqualDate, boolean qualExist, SimpleDateFormat sdf) throws MilException, ParseException {

        // 限度額適用認定証有効開始年月日チェックOKフラグ
        boolean limitAvailableDateOK = false;
        // 限度額適用認定証有効終了年月日チェックOKフラグ
        boolean limitInvalidDateOK = false;

		// 保険者番号（限度額認定証）
		String cardInsurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(cardInsurerNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_INSURER_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(cardInsurerNumber)) {
			if (!HimStringCheckUtil.checkNum(cardInsurerNumber)) {
				// 半角数字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_INSURER_NUMBER);
			} else if (!HimStringCheckUtil.checkLength(cardInsurerNumber, HimConstant.LENGTH_LIMIT_CARD_INSURER_NUMBER)) {
				// 8文字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_INSURER_NUMBER, String.valueOf(HimConstant.LENGTH_LIMIT_CARD_INSURER_NUMBER));
			}
		}

		// 被保険者証記号（限度額認定証）
		String cardInsuredProofSymbol = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL);
		// 設定なしの場合、チェックしない。
		if (HimStringCheckUtil.checkEmpty(cardInsuredProofSymbol)) {
			// 20文字以内であること。
			if (!HimStringCheckUtil.checkMaxLength(cardInsuredProofSymbol, HimConstant.LENGTH_LIMIT_CARD_INSURED_PROOF_SYMBOL)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_INSURED_PROOF_SYMBOL, String.valueOf(HimConstant.LENGTH_LIMIT_CARD_INSURED_PROOF_SYMBOL));
			}
		}

		// 被保険者証番号（限度額認定証）
		String cardInsuredProofNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_NUMBER);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(cardInsuredProofNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_INSURED_PROOF_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (HimStringCheckUtil.checkEmpty(cardInsuredProofNumber)) {
			if (!HimStringCheckUtil.checkMaxLength(cardInsuredProofNumber, HimConstant.LENGTH_LIMIT_CARD_INSURED_PROOF_NUMBER)){
				// 20文字以内であること。
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_NUMBER, CmnMsgConstant.MSG_CMN_W2129,
						strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_INSURED_PROOF_NUMBER, String.valueOf(HimConstant.LENGTH_LIMIT_CARD_INSURED_PROOF_NUMBER));
			}
		}

		// 被保険者証枝番（限度額認定証）
		String branchNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_BRANCH_NUMBER);
		// 後期高齢者医療以外の場合必須
		if (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE) != null && dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE).startsWith("B")) {
			if (HimStringCheckUtil.checkEmpty(branchNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_BRANCH_NUMBER, HimMsgConstant.MSG_HIM_W0145, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_BRANCH_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(branchNumber)) {
			if  (!HimStringCheckUtil.checkNum(branchNumber)) {
				// 半角数字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_BRANCH_NUMBER);
			} else if (!HimStringCheckUtil.checkLength(branchNumber, HimConstant.LENGTH_LIMIT_CARD_BRANCH_NUMBER)) {
				// 2文字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_BRANCH_NUMBER, String.valueOf(HimConstant.LENGTH_LIMIT_CARD_BRANCH_NUMBER));
			}
		}

		// 限度額適用認定証区分
		String limitOfCopaymentProofDivision = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_OF_COPAYMENT_PROOF_DIVISION);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(limitOfCopaymentProofDivision)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_OF_COPAYMENT_PROOF_DIVISION, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_DIVISION);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(limitOfCopaymentProofDivision)) {
			if (!limitOfCopaymentProofDivision.matches(HimConstant.CHECK_LIMIT_OF_COPAYMENT_PROOF_DIVISION)) {
				// 数値(01,02,03)
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_OF_COPAYMENT_PROOF_DIVISION, CmnMsgConstant.MSG_CMN_W2125,
					strFileLineNumber, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_DIVISION);
			}
		}

		// 限度額適用認定証交付年月日
		String limitIssuanceDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_ISSUANCE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(limitIssuanceDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_ISSUANCE_DATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_LIMIT_ISSUANCE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(limitIssuanceDate)) {
			if (!checkDateFormat(limitIssuanceDate, sdf)) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_ISSUANCE_DATE, strFileLineNumber, HimConstant.ITEM_LIMIT_ISSUANCE_DATE);
			}
		}

		// 限度額適用認定証有効開始年月日
		String limitAvailableDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_AVAILABLE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(limitAvailableDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_AVAILABLE_DATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_LIMIT_AVAILABLE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(limitAvailableDate)) {
		    limitAvailableDateOK = checkDateFormat(limitAvailableDate, sdf);
			if (!limitAvailableDateOK) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_AVAILABLE_DATE, strFileLineNumber, HimConstant.ITEM_LIMIT_AVAILABLE_DATE);
			}
		}
		// 限度額適用認定証有効終了年月日
		String limitInvalidDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(limitInvalidDate)) {
		    limitInvalidDateOK = checkDateFormat(limitInvalidDate, sdf);
			if (!limitInvalidDateOK) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE, strFileLineNumber, HimConstant.ITEM_LIMIT_INVALID_DATE);
			}
		}
		// 処理種別12：加入者情報のレコード種別単位更新の場合
		if (HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			if(qualExist) {
				if (!"".equals(qualDisqualDate) && qualDisqualDate != null) {
					// 資格喪失状態
					if (HimStringCheckUtil.checkEmpty(limitInvalidDate)) {
						// 必須
						ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
								strFileLineNumber, HimConstant.ITEM_LIMIT_INVALID_DATE);
					} else if (limitInvalidDateOK && limitInvalidDate.compareTo(qualDisqualDate) > 0) {
					    // 妥当な日付が設定されている場合かつ、
						// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
						ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
								strFileLineNumber, HimConstant.ITEM_LIMIT_INVALID_DATE);
					}
				}
			} else {
				String lastDisqualDate = checkDisqualifiedQual(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE), dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PERSONAL_NUMBER), sdf);
				if (!"".equals(lastDisqualDate)) {
					// 資格喪失状態
					if (HimStringCheckUtil.checkEmpty(limitInvalidDate)) {
						// 必須
						ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
								strFileLineNumber, HimConstant.ITEM_LIMIT_INVALID_DATE);
					} else if (limitInvalidDateOK && limitInvalidDate.compareTo(lastDisqualDate) > 0) {
					    // 妥当な日付が設定されている場合かつ、
						// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
						ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
								strFileLineNumber, HimConstant.ITEM_LIMIT_INVALID_DATE);
					}
				}
			}
		}
		// 処理種別11：加入者情報の登録または処理種別13:加入者の全体更新の場合
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))
				|| HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			if (!"".equals(qualDisqualDate) && qualDisqualDate != null) {
				// 資格喪失状態
				if (HimStringCheckUtil.checkEmpty(limitInvalidDate)) {
					// 必須
					ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
							strFileLineNumber, HimConstant.ITEM_LIMIT_INVALID_DATE);
				} else if (limitInvalidDateOK && limitInvalidDate.compareTo(qualDisqualDate) > 0) {
				    // 妥当な日付が設定されている場合かつ、
					// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
					ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
							strFileLineNumber, HimConstant.ITEM_LIMIT_INVALID_DATE);
				}
			}
		}

		// 限度額適用認定証有効開始年月日、限度額適用認定証有効終了年月日の両方が設定され、入力チェックOKの場合のみ、過去日チェックを行う。
		if (limitAvailableDateOK && limitInvalidDateOK) {
			if (!HimDateCheckUtil.checkPastDate(sdf.parse(limitAvailableDate), sdf.parse(limitInvalidDate))) {
				// 限度額適用認定証有効開始年月日が限度額適用認定証有効終了年月日より過去日であること。
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_INVALID_DATE,
						CmnMsgConstant.MSG_CMN_W2158, strFileLineNumber, HimConstant.ITEM_LIMIT_INVALID_DATE,
						HimConstant.ITEM_LIMIT_AVAILABLE_DATE);
			}
		}

		// 限度額適用認定証適用区分
		String segmentOfUpdate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_SEGMENT_OF_UPDATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(segmentOfUpdate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_SEGMENT_OF_UPDATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_LIMIT_OF_COPAYMENT_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_LIMIT_SEGMENT_OF_UPDATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(segmentOfUpdate)) {
			if (!segmentOfUpdate.matches(HimConstant.CHECK_SEGMENT_OF_UPDATE)) {
				// 数値(A01～A06,A99,B01～B08)
				ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_SEGMENT_OF_UPDATE, CmnMsgConstant.MSG_CMN_W2125,
					strFileLineNumber, HimConstant.ITEM_LIMIT_SEGMENT_OF_UPDATE);
			}
		}
		// 限度額適用認定証長期入院該当年月日
		String longHospitalizationDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LONG_HOSPITALIZATION_DATE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(longHospitalizationDate)) {
			if (!checkDateFormat(longHospitalizationDate, sdf)) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LONG_HOSPITALIZATION_DATE, strFileLineNumber, HimConstant.ITEM_LONG_HOSPITALIZATION_DATE);
			}

		}
		// 限度額適用認定証回収年月日
		String limitCollectDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_COLLECT_DATE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(limitCollectDate)) {
			if (!checkDateFormat(limitCollectDate, sdf)) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_COLLECT_DATE, strFileLineNumber, HimConstant.ITEM_LIMIT_COLLECT_DATE);
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：特定疾病療養受療証情報レコード存在チェック。</dd>
	 * <dd>メソッド説明：特定疾病療養受療証情報レコードを存在チェックします。</dd>
	 * <dd>備考：PR_HIM_01_202</dd>
	 * </dl>
	 * @param dataList
	 * @param checkQualDatas
	 * @param lineNumber
	 * @param strFileLineNumber
	 * @param ie
	 * @throws MilException
	 * @throws ParseException
	 */
	private static void checkSpecificDiseaseInputBeanList(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, String qualDisqualDate, boolean qualExist, SimpleDateFormat sdf) throws MilException, ParseException {

        // 特定疾病療養受療証有効開始年月日チェックOKフラグ
        boolean specificAvailableDateOK = false;
        // 特定疾病療養受療証有効終了年月日チェックOKフラグ
        boolean specificInvalidDateOK = false;

		// 保険者番号（特定疾病療養受療証）
		String cardInsurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(cardInsurerNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_INSURER_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(cardInsurerNumber)) {
			if (!HimStringCheckUtil.checkNum(cardInsurerNumber)) {
				// 半角数字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_INSURER_NUMBER);
			} else if (!HimStringCheckUtil.checkLength(cardInsurerNumber, HimConstant.LENGTH_ELDERLY_CARD_INSURER_NUMBER)) {
				// 8文字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_INSURER_NUMBER, String.valueOf(HimConstant.LENGTH_SPECIFIC_CARD_INSURER_NUMBER));
			}
		}

		// 被保険者証記号（特定疾病療養受療証）
		String cardInsuredProofSymbol = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL);
		// 設定なしの場合、チェックしない。
		if (HimStringCheckUtil.checkEmpty(cardInsuredProofSymbol)) {
			// 20文字以内であること。
			if (!HimStringCheckUtil.checkMaxLength(cardInsuredProofSymbol, HimConstant.LENGTH_SPECIFIC_CARD_INSURED_PROOF_SYMBOL)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_INSURED_PROOF_SYMBOL, String.valueOf(HimConstant.LENGTH_SPECIFIC_CARD_INSURED_PROOF_SYMBOL));
			}
		}

		// 被保険者証番号（特定疾病療養受療証）
		String cardInsuredProofNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_NUMBER);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(cardInsuredProofNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_NUMBER, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_INSURED_PROOF_NUMBER);
			}
		}
		if (!HimStringCheckUtil.checkMaxLength(cardInsuredProofNumber, HimConstant.LENGTH_SPECIFIC_CARD_INSURED_PROOF_NUMBER)){
			// 20文字以内であること。
			ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_NUMBER, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_INSURED_PROOF_NUMBER, String.valueOf(HimConstant.LENGTH_SPECIFIC_CARD_INSURED_PROOF_NUMBER));
		}

		// 被保険者証枝番（特定疾病療養受療証）
		String cardBranchNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_BRANCH_NUMBER);
		// 後期高齢者医療以外の場合必須
		if (dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE) != null && dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE).startsWith("B")) {
			if (HimStringCheckUtil.checkEmpty(cardBranchNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_BRANCH_NUMBER, HimMsgConstant.MSG_HIM_W0145, HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_BRANCH_NUMBER);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(cardBranchNumber)) {
			if  (!HimStringCheckUtil.checkNum(cardBranchNumber)) {
				// 半角数字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_BRANCH_NUMBER);
			} else if (!HimStringCheckUtil.checkLength(cardBranchNumber, HimConstant.LENGTH_SPECIFIC_CARD_BRANCH_NUMBER)) {
				// 2文字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_BRANCH_NUMBER, String.valueOf(HimConstant.LENGTH_SPECIFIC_CARD_BRANCH_NUMBER));
			}
		}

		// 特定疾病療養受療証交付年月日
		String specificIssuanceDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_ISSUANCE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(specificIssuanceDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_ISSUANCE_DATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_ISSUANCE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(specificIssuanceDate)) {
			if (!checkDateFormat(specificIssuanceDate, sdf)) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_ISSUANCE_DATE, strFileLineNumber, HimConstant.ITEM_SPECIFIC_ISSUANCE_DATE);
			}
		}
		// 特定疾病療養受療証有効開始年月日
		String specificAvailableDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_AVAILABLE_DATE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(specificAvailableDate)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_AVAILABLE_DATE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_AVAILABLE_DATE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(specificAvailableDate)) {
		    specificAvailableDateOK = checkDateFormat(specificAvailableDate, sdf);
			if (!specificAvailableDateOK) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_AVAILABLE_DATE, strFileLineNumber, HimConstant.ITEM_SPECIFIC_AVAILABLE_DATE);
			}
		}
		// 特定疾病療養受療証有効終了年月日
		String specificInvalidDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(specificInvalidDate)) {
		    specificInvalidDateOK = checkDateFormat(specificInvalidDate, sdf);
			if (!specificInvalidDateOK) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE, strFileLineNumber, HimConstant.ITEM_SPECIFIC_INVALID_DATE);
			}
		}
		// 処理種別12：加入者情報のレコード種別単位更新の場合
		if (HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			if(qualExist) {
				if (!"".equals(qualDisqualDate) && qualDisqualDate != null) {
					// 資格喪失状態
					if (HimStringCheckUtil.checkEmpty(specificInvalidDate)) {
						// 必須
						ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
								strFileLineNumber, HimConstant.ITEM_SPECIFIC_INVALID_DATE);
					} else if (specificInvalidDateOK && specificInvalidDate.compareTo(qualDisqualDate) > 0) {
					    // 妥当な日付が設定されている場合かつ、
						// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
						ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
								strFileLineNumber, HimConstant.ITEM_SPECIFIC_INVALID_DATE);
					}
				}
			} else {
				String lastDisqualDate = checkDisqualifiedQual(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE), dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PERSONAL_NUMBER), sdf);
				if (!"".equals(lastDisqualDate)) {
					// 資格喪失状態
					if (HimStringCheckUtil.checkEmpty(specificInvalidDate)) {
						// 必須
						ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
								strFileLineNumber, HimConstant.ITEM_SPECIFIC_INVALID_DATE);
					} else if (specificInvalidDateOK && specificInvalidDate.compareTo(lastDisqualDate) > 0) {
					    // 妥当な日付が設定されている場合かつ、
						// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
						ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
								strFileLineNumber, HimConstant.ITEM_SPECIFIC_INVALID_DATE);
					}
				}
			}
		}
		// 処理種別11：加入者情報の登録または処理種別13:加入者の全体更新の場合
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))
				|| HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			if (!"".equals(qualDisqualDate) && qualDisqualDate != null) {
				// 資格喪失状態
				if (HimStringCheckUtil.checkEmpty(specificInvalidDate)) {
					// 必須
					ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE, HimMsgConstant.MSG_HIM_W0104,
							strFileLineNumber, HimConstant.ITEM_SPECIFIC_INVALID_DATE);
				} else if (specificInvalidDateOK && specificInvalidDate.compareTo(qualDisqualDate) > 0) {
				    // 妥当な日付が設定されている場合かつ、
					// 資格が喪失している場合、最新の喪失日以後の有効終了年月日は設定できない
					ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE, HimMsgConstant.MSG_HIM_W0115,
							strFileLineNumber, HimConstant.ITEM_SPECIFIC_INVALID_DATE);
				}
			}
		}

		// 特定疾病療養受療証有効開始年月日、特定疾病療養受療証有効終了年月日の両方が設定され、入力チェックOKの場合のみ、過去日チェックを行う。
		if (specificAvailableDateOK && specificInvalidDateOK) {
			if (!HimDateCheckUtil.checkPastDate(sdf.parse(specificAvailableDate), sdf.parse(specificInvalidDate))) {
				// 特定疾病療養受療証有効開始年月日が特定疾病療養受療証有効終了年月日より過去日であること。
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_INVALID_DATE,
						CmnMsgConstant.MSG_CMN_W2158, strFileLineNumber, HimConstant.ITEM_SPECIFIC_INVALID_DATE,
						HimConstant.ITEM_SPECIFIC_AVAILABLE_DATE);
			}
		}

		// 特定疾病療養受療証認定疾病区分
		String segmentOfDisease = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_SEGMENT_OF_DISEASE);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(segmentOfDisease)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SEGMENT_OF_DISEASE, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_SEGMENT_OF_DISEASE);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(segmentOfDisease)) {
			if (!segmentOfDisease.matches(HimConstant.CHECK_SPECIFIC_SEGMENT_OF_DISEASE)) {
				// 数値(1～3)
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SEGMENT_OF_DISEASE, CmnMsgConstant.MSG_CMN_W2125,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_SEGMENT_OF_DISEASE);
			}
		}
		// 特定疾病療養受療証自己負担限度額
		String limitOfCopayment = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_LIMIT_OF_COPAYMENT);
		if (HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))){
			// 処理種別コードが11,12,13の場合必須
			if (HimStringCheckUtil.checkEmpty(limitOfCopayment)) {
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_LIMIT_OF_COPAYMENT, HimMsgConstant.MSG_HIM_W0099, HimConstant.ITEM_SPECIFIC_DISEASE_PROOF_INFO,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_LIMIT_OF_COPAYMENT);
			}
		}
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(limitOfCopayment)) {
			if  (!HimStringCheckUtil.checkNum(limitOfCopayment)) {
				// 半角数字であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_LIMIT_OF_COPAYMENT, CmnMsgConstant.MSG_CMN_W2127,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_LIMIT_OF_COPAYMENT);
			} else if (!HimStringCheckUtil.checkMaxLength(limitOfCopayment, HimConstant.LENGTH_SPECIFIC_LIMIT_OF_COPAYMENT)) {
				// 6文字以内であること
				ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_LIMIT_OF_COPAYMENT, CmnMsgConstant.MSG_CMN_W2129,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_LIMIT_OF_COPAYMENT, String.valueOf(HimConstant.LENGTH_SPECIFIC_LIMIT_OF_COPAYMENT));
			}
		}
		// 特定疾病療養受療証回収年月日
		String specificCollectDate = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_COLLECT_DATE);
		// 設定なしの場合、チェックしない。
		if (!HimStringCheckUtil.checkEmpty(specificCollectDate)) {
			if (!checkDateFormat(specificCollectDate, sdf)) {
			    // 日付として妥当ではない場合
    			setDateFormatError(ie, HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_COLLECT_DATE, strFileLineNumber, HimConstant.ITEM_SPECIFIC_COLLECT_DATE);
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイルレコード識別番号入力チェック。</dd>
	 * <dd>メソッド説明：加入者情報一括登録ファイルのレコード識別番号の入力チェックをします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param recordIdNumber レコード識別番号
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 */
	private static void checkRecordIdNumber(String recordIdNumber, String fileLineNumber, MilInputErrorException ie) {

		// レコード識別番号のチェックをおこないます。
		// 必須項目であること。
		if (HimStringCheckUtil.checkEmpty(recordIdNumber)) {
			ie.addErrMsgList(HimConstant.PROPERTY_RECORD_ID_NUMBER, CmnMsgConstant.MSG_CMN_W2124,
				fileLineNumber, HimConstant.ITEM_RECORD_ID_NUMBER);
		} else {
			// 半角英数字であること。
			if (!HimStringCheckUtil.checkAlphaNum(recordIdNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_RECORD_ID_NUMBER, CmnMsgConstant.MSG_CMN_W2169,
					fileLineNumber, HimConstant.ITEM_RECORD_ID_NUMBER);
			}

			// 16文字以内であること。
			if (!HimStringCheckUtil.checkMaxLength(recordIdNumber, HimConstant.LONG_RECORDIDNUMBER)) {
				ie.addErrMsgList(HimConstant.PROPERTY_RECORD_ID_NUMBER, CmnMsgConstant.MSG_CMN_W2129,
					fileLineNumber, HimConstant.ITEM_RECORD_ID_NUMBER, String.valueOf(HimConstant.LONG_RECORDIDNUMBER));
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイルレコード種別コード入力チェック。</dd>
	 * <dd>メソッド説明：加入者情報一括登録ファイルのレコード種別コードの入力チェックをします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param recordCategory レコード種別コード
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 */
	private static void checkRecordCategory(String recordCategory, String fileLineNumber, MilInputErrorException ie) {

		// 必須項目であること。
		if (HimStringCheckUtil.checkEmpty(recordCategory)) {
			ie.addErrMsgList(HimConstant.PROPERTY_RECORD_CATEGORY, CmnMsgConstant.MSG_CMN_W2124,
				fileLineNumber, HimConstant.ITEM_RECORD_CATEGORY);
		} else if (!HimCodeCheckUtil.checkRecordCategoryDefinition(recordCategory)) {
			// レコード種別コードが設定されていること。
			ie.addErrMsgList(HimConstant.PROPERTY_RECORD_CATEGORY, CmnMsgConstant.MSG_CMN_W2125,
				fileLineNumber, HimConstant.ITEM_RECORD_CATEGORY);
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイル処理種別コード入力チェック。</dd>
	 * <dd>メソッド説明：加入者情報一括登録ファイルの処理種別コードの入力チェックをします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param processCategory 処理種別コード
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 */
	private static void checkProcessCategory(String processCategory, String fileLineNumber, MilInputErrorException ie) {

		// 処理種別コードのチェックをおこないます。
		// 必須項目であること。
		if (HimStringCheckUtil.checkEmpty(processCategory)) {
			ie.addErrMsgList(HimConstant.PROPERTY_PROCESS_CATEGORY, CmnMsgConstant.MSG_CMN_W2124,
				fileLineNumber, HimConstant.ITEM_PROCESS_CATEGORY);
		} else if (!HimCodeCheckUtil.checkProcessCategory2Definition(processCategory)) {
			// 処理種別コードが設定されていること。
			ie.addErrMsgList(HimConstant.PROPERTY_PROCESS_CATEGORY, CmnMsgConstant.MSG_CMN_W2125,
				fileLineNumber, HimConstant.ITEM_PROCESS_CATEGORY);
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイル、保険者コード入力項目チェック。</dd>
	 * <dd>メソッド説明：保険者コードの入力項目をチェックします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param insurerCode 保険者コード
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 */
	private static void checkInsurerCode(String insurerCode, String fileLineNumber, MilInputErrorException ie) {

		// 保険者コードのチェックをおこないます。
		// 必須項目であること(保険者コード)
		if (HimStringCheckUtil.checkEmpty(insurerCode)) {
			ie.addErrMsgList(HimConstant.PROPERTY_INSURER_CODE, CmnMsgConstant.MSG_CMN_W2124,
				fileLineNumber, HimConstant.ITEM_INSURER_CODE);
		} else {
			// 半角英数字であること(保険者コード)
			if (!HimStringCheckUtil.checkAlphaNum(insurerCode)) {
				ie.addErrMsgList(HimConstant.PROPERTY_INSURER_CODE, CmnMsgConstant.MSG_CMN_W2127,
					fileLineNumber, HimConstant.ITEM_INSURER_CODE);
			}

			// 8文字であること(保険者コード)
			if (!HimStringCheckUtil.checkLength(insurerCode, HimConstant.LONG_INSURERCODE)) {
				ie.addErrMsgList(HimConstant.PROPERTY_INSURER_CODE, CmnMsgConstant.MSG_CMN_W2126,
					fileLineNumber, HimConstant.ITEM_INSURER_CODE, String.valueOf(HimConstant.LONG_INSURERCODE));
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイル、被保険者枝番入力項目チェック。</dd>
	 * <dd>メソッド説明：被保険者枝番の入力項目をチェックします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param insuredBranchNumber 被保険者枝番
	 * @param processCategory 処理種別コード
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 */
	private static void checkInsureBranchNumber(String insuredBranchNumber, String processCategory,
		String fileLineNumber, MilInputErrorException ie) {

		// 被保険者枝番入力チェック
		if (!HimStringCheckUtil.checkEmpty(insuredBranchNumber)) {
			// 半角数字であること(被保険者枝番)
			if (!HimStringCheckUtil.checkNum(insuredBranchNumber)) {
				ie.addErrMsgList(HimConstant.PROPERTY_INSURED_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					fileLineNumber, HimConstant.ITEM_INSURED_BRANCH_NUMBER);
			}

			// 16文字であること(被保険者枝番)
			if (!HimStringCheckUtil.checkLength(insuredBranchNumber, HimConstant.LONG_INSUREDBRANCHNUMBER)) {
				ie.addErrMsgList(HimConstant.PROPERTY_INSURED_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					fileLineNumber, HimConstant.ITEM_INSURED_BRANCH_NUMBER, String.valueOf(HimConstant.LONG_INSUREDBRANCHNUMBER));
			}
		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(processCategory)
					|| HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(processCategory)
					|| HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_DELETE.equals(processCategory)
					|| HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_DELETE.equals(processCategory)
					|| HimConstant.CODE_SUB_STATUS_CODE_NEW_PERSONAL_NUMBER_CORRECT.equals(processCategory)){

			// 処理種別コード12,13,14,15,16の場合、必須項目であること(被保険者枝番)
			ie.addErrMsgList(HimConstant.PROPERTY_INSURED_BRANCH_NUMBER, CmnMsgConstant.MSG_CMN_W2124,
					fileLineNumber, HimConstant.ITEM_INSURED_BRANCH_NUMBER);
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイル、個人番号入力チェック。</dd>
	 * <dd>メソッド説明：個人番号の項目を入力チェックします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param personalNumber 個人番号
	 * @param afterPersonalNumber 更新後個人番号
	 * @param processCategory 処理種別コード
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 */
	private static void checkPersonalNumber(String personalNumber, String afterPersonalNumber,
					String processCategory, String fileLineNumber, MilInputErrorException ie) {

		// 個人番号入力チェック
		if (!HimStringCheckUtil.checkEmpty(personalNumber)) {
			boolean isCheckDigit = true;
			// 半角数字であること(個人番号)
			if (!HimStringCheckUtil.checkNum(personalNumber)) {
				isCheckDigit = false;
				ie.addErrMsgList(HimConstant.PROPERTY_PERSONAL_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					fileLineNumber, HimConstant.ITEM_PERSONAL_NUMBER);
			}

			// 12文字であること(個人番号)
			if (!HimStringCheckUtil.checkLength(personalNumber, HimConstant.LONG_PERSONALNUMBER)) {
				ie.addErrMsgList(HimConstant.PROPERTY_PERSONAL_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					fileLineNumber, HimConstant.ITEM_PERSONAL_NUMBER, String.valueOf(HimConstant.LONG_PERSONALNUMBER));
			} else if (isCheckDigit && !HimStringCheckUtil.checkMyNumDigit(personalNumber)) {
				// 設定された内容が個人番号の形式になっていること。
				ie.addErrMsgList(HimConstant.PROPERTY_PERSONAL_NUMBER, CmnMsgConstant.MSG_CMN_W2128,
					fileLineNumber, HimConstant.ITEM_PERSONAL_NUMBER);
			}

		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(processCategory) ||
				(HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(processCategory) && !HimStringCheckUtil.checkEmpty(afterPersonalNumber)) ||
				(HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(processCategory) && !HimStringCheckUtil.checkEmpty(afterPersonalNumber)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_PERSONAL_NUMBER_CORRECT.equals(processCategory)) {
			// 必須項目であること(個人番号)
			ie.addErrMsgList(HimConstant.PROPERTY_PERSONAL_NUMBER, CmnMsgConstant.MSG_CMN_W2124,
					fileLineNumber, HimConstant.ITEM_PERSONAL_NUMBER);
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイル、更新後個人番号入力チェック。</dd>
	 * <dd>メソッド説明：更新後個人番号の項目を入力チェックします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param afterPersonalNumber 更新後個人番号
	 * @param personalNumber 個人番号
	 * @param processCategory 処理種別コード
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 */
	private static void checkAfterPersonalNumber(String afterPersonalNumber, String personalNumber,
			String processCategory, String fileLineNumber, MilInputErrorException ie) {

		// 更新後個人番号入力チェック
		if (!HimStringCheckUtil.checkEmpty(afterPersonalNumber)) {
			boolean isCheckDigit = true;
			// 半角数字であること(更新後個人番号)
			if (!HimStringCheckUtil.checkNum(afterPersonalNumber)) {
				isCheckDigit = false;
				ie.addErrMsgList(HimConstant.PROPERTY_AFTER_PERSONAL_NUMBER, CmnMsgConstant.MSG_CMN_W2127,
					fileLineNumber, HimConstant.ITEM_AFTER_PERSONAL_NUMBER);
			}

			// 12文字であること(更新後個人番号)
			if (!HimStringCheckUtil.checkLength(afterPersonalNumber, HimConstant.LONG_PERSONALNUMBER)) {
				ie.addErrMsgList(HimConstant.PROPERTY_AFTER_PERSONAL_NUMBER, CmnMsgConstant.MSG_CMN_W2126,
					fileLineNumber, HimConstant.ITEM_AFTER_PERSONAL_NUMBER, String.valueOf(HimConstant.LONG_PERSONALNUMBER));
			} else if (isCheckDigit && !HimStringCheckUtil.checkMyNumDigit(afterPersonalNumber)) {
				// 設定された内容が更新後個人番号の形式になっていること。
				ie.addErrMsgList(HimConstant.PROPERTY_AFTER_PERSONAL_NUMBER, CmnMsgConstant.MSG_CMN_W2128,
					fileLineNumber, HimConstant.ITEM_AFTER_PERSONAL_NUMBER);
			}

			// 以下のいずれかの条件のとき、設定された個人番号と更新後個人番号の値が同一である。
			// 処理種別が「加入者情報の更新」のとき、個人番号が設定されている場合
			if (HimCodeCheckUtil.checkPersonalAndAfterPersonalNum(personalNumber, afterPersonalNumber,
					processCategory)) {
				ie.addErrMsgList(HimConstant.PROPERTY_AFTER_PERSONAL_NUMBER, HimMsgConstant.MSG_HIM_W0006,
					fileLineNumber);
			}
		} else if ((HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(processCategory) && !HimStringCheckUtil.checkEmpty(afterPersonalNumber)) ||
				(HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(processCategory) && !HimStringCheckUtil.checkEmpty(afterPersonalNumber) ||
						HimConstant.CODE_SUB_STATUS_CODE_NEW_PERSONAL_NUMBER_CORRECT.equals(processCategory))){
				// 必須項目であること(更新後個人番号)
			ie.addErrMsgList(HimConstant.PROPERTY_AFTER_PERSONAL_NUMBER, CmnMsgConstant.MSG_CMN_W2124,
				fileLineNumber, HimConstant.ITEM_AFTER_PERSONAL_NUMBER);
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイル、資格取得年月日の入力チェック。</dd>
	 * <dd>メソッド説明：資格取得年月日を入力チェックします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param dataList データリスト
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 * @return チェックOK：日付型 チェックNG、または設定値なし：null
	 */
	private static Date checkQualificationDate(List<String> dataList, String fileLineNumber, MilInputErrorException ie, SimpleDateFormat sdf) {

		Date result = null;

		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_QUALIFICATION_DATE))) {
			// 誤った日付が設定（例：2月30日、4月31日など）されていないこと
			try {
				result = sdf.parse(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_QUALIFICATION_DATE));
			} catch (Exception e) {
				// 日付変換例外
				ie.addErrMsgList(HimConstant.PROPERTY_QUALIFICATION2_DATE, CmnMsgConstant.MSG_CMN_W2131,
					fileLineNumber, HimConstant.ITEM_QUALIFICATION2_DATE);
			}
		} else if (HimConstant.CODE_SUB_STATUS_CODE_NEW_REGISTER.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY)) ||
				HimConstant.CODE_SUB_STATUS_CODE_NEW_ALL_UPDATE.equals(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
			// 必須チェック
			ie.addErrMsgList(HimConstant.PROPERTY_QUALIFICATION2_DATE, HimMsgConstant.MSG_HIM_W0034,
				fileLineNumber, HimConstant.ITEM_QUALIFICATION2_DATE);
		}
		return result;
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者情報一括登録ファイル、資格喪失年月日の入力チェック。</dd>
	 * <dd>メソッド説明：資格喪失年月日を入力チェックします。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param dataList データリスト
	 * @param fileLineNumber ファイル行番号
	 * @param ie 入力エラー例外
	 * @return チェックOK：日付型 チェックNG、または設定値なし：null
	 * @throws 日付比較時のエラー
	 */
	private static Date checkDisqualificationDate(List<String> dataList, String fileLineNumber, MilInputErrorException ie, SimpleDateFormat sdf) throws ParseException {

		Date result = null;

		if (!HimStringCheckUtil.checkEmpty(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE))) {
			// 誤った日付が設定（例：2月30日、4月31日など）されていないこと
			try {
				result = sdf.parse(dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_DISQUALIFICATION_DATE));

			} catch (Exception e) {
				// 日付変換例外
				ie.addErrMsgList(HimConstant.PROPERTY_DISQUALIFICATION2_DATE, CmnMsgConstant.MSG_CMN_W2131,
					fileLineNumber, HimConstant.ITEM_DISQUALIFICATION2_DATE);
			}
		}
		return result;
	}

	/**
	 * <dl>
	 * <dd>メソッド名：日付妥当性チェック</dd>
	 * <dd>メソッド説明：誤った日付を入力してないかチェックします。</dd>
	 * </dl>
	 * @param dateStr
	 * @return result 正常な日付の場合trueを返却します。
	 */
	private static boolean checkDateFormat(String dateStr, SimpleDateFormat sdf) {
	    boolean result = true;
		try {
			sdf.parse(dateStr);

		} catch (Exception e) {
			// 不正日付
			result = false;
		}
		return result;
	}

	/**
	 * <dl>
	 * <dd>メソッド名：日付形式エラー設定</dd>
	 * <dd>メソッド説明：日付形式エラー時のエラー情報を設定します。</dd>
	 * </dl>
	 * @param ie
	 * @param property
	 * @param strFileLineNumber
	 * @param item
	 */
	private static void setDateFormatError(MilInputErrorException ie, String property, String strFileLineNumber, String item) {
	    if (strFileLineNumber == null) {
			ie.addErrMsgList(property, CmnMsgConstant.MSG_CMN_W2105, item);
		} else {
			ie.addErrMsgList(property, CmnMsgConstant.MSG_CMN_W2131, strFileLineNumber, item);
		}
		ie.setDetailCode(MilDtlCodeConstant.BUS_ERROR_101004);
	}

	/**
	 * <dl>
	 * <dd>メソッド名：日付形式エラー設定</dd>
	 * <dd>メソッド説明：日付形式エラー時のエラー情報を設定します。</dd>
	 * </dl>
	 * @param ie
	 * @param property
	 * @param item
	 */
	private static void setDateFormatError(MilInputErrorException ie, String property, String item) {
	    setDateFormatError(ie, property, null, item);
	}

	/**
	 * <dl>
	 * <dd>メソッド名：レコード種別の組み合わせ判定</dd>
	 * <dd>メソッド説明：レコード種別の組み合わせをチェックします。</dd>
	 * </dl>
	 * @param segmentOfSubscriber
	 * @param processCategory
	 * @param sortRecordList
	 * @param ie
	 */
	private static void checkRecordCombination(String segmentOfSubscriber, String processCategory,
			List<Integer> sortRecordList, int subCount, MilInputErrorException ie,int subsFileNumber) {
		// 識別番号ごと先頭行合わせ
		int rowNumber = subsFileNumber;
		int rowSubsNumber = subsFileNumber;

		// レコード種別の組み合わせ判定
		if ((processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER) || processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE))) {
			// 加入者の場合で、処理種別コード11,13
			String check = "";
			for (int i = 0; i < sortRecordList.size(); i++) {
				check = check + String.valueOf(sortRecordList.get(i));
				if("0".equals(segmentOfSubscriber)) {
					if (sortRecordList.get(i) == 9) {
						// 不可エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0147,String.valueOf(rowNumber));
					}
				}
					rowNumber++;
			}
			if(!check.contains("4")) {
				// 必須エラーメッセージ
				ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0137);
			}
			if ( !check.contains("2") || check.contains("22")) {
				// 必須エラーメッセージ
				ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0141);
			}
			if(!check.contains("1") || check.contains("11")) {
				// 必須エラーメッセージ
				ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0140);
			}
		} else if (processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY)) {
			// 加入者の場合で、処理種別コード12
			String check = "";
			for (int i = 0; i < sortRecordList.size(); i++) {
				check = check + String.valueOf(sortRecordList.get(i));
				if("0".equals(segmentOfSubscriber)) {
					if (sortRecordList.get(i) == 9) {
						// 不可エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0147,String.valueOf(rowNumber));
						break;
					}
				}
				rowNumber++;
			}
			if(check.contains("11")) {
				// 必須エラーメッセージ
				ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0140);
			}
			if(check.contains("22")) {
				// 必須エラーメッセージ
				ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0141);
			}
		} else if (processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY_SUBS_DELETE_EACH_RECORD_CATEGORY)) {
			//処理種別コード14
			String check = "";
			for (int i = 0; i < sortRecordList.size(); i++) {
				check = check + String.valueOf(sortRecordList.get(i));
				if("0".equals(segmentOfSubscriber)) {
					if (sortRecordList.get(i) == 1 || sortRecordList.get(i) == 2 || sortRecordList.get(i) == 4 || sortRecordList.get(i) == 9) {
						// 不可エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0147,String.valueOf(rowNumber));
					}
				}else if("1".equals(segmentOfSubscriber) || "2".equals(segmentOfSubscriber)) {
					if (sortRecordList.size() != 0) {
						// 不可エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0147,String.valueOf(rowNumber));
					}
				}
				rowNumber++;
			}
		} else if ((processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY_SUBSCRIBER_DELETE) || processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY_SUBS_CORRECT_PERSONAL_NUMBER))) {
			// 処理種別コード15,16
			String check = "";
			for (int i = 0; i < sortRecordList.size(); i++) {
				check = check + String.valueOf(sortRecordList.get(i));
			}
			for (int i = 0; i < sortRecordList.size(); i++) {
				if (sortRecordList.get(i) == 1 || sortRecordList.get(i) == 2 || sortRecordList.get(i) == 3|| sortRecordList.get(i) == 4 ||
						sortRecordList.get(i) == 5 || sortRecordList.get(i) == 6 || sortRecordList.get(i) == 7 || sortRecordList.get(i) == 8) {
					// 不可エラーメッセージ
					ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0147,String.valueOf(rowNumber));
				}
				rowNumber++;
			}
			if (!check.contains("9")) {
				// 必須エラーメッセージ
				ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0137);
			}
		}
		if ((processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY2_SUBS_REGISTER) ||
						processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY_SUBS_UPDATE_EACH_RECORD_CATEGORY) ||
								processCategory.equals(HimConstant.ITEM_PROCESS_CATEGORY_SUBS_ALL_UPDATE))) {
			// 加入予定者(仮登録)、処理種別コード11,12,13
			String check = "";
			//加入予定者(仮登録)、加入者の世帯員の場合
				for (int i = 0; i < sortRecordList.size(); i++) {
					check = check + String.valueOf(sortRecordList.get(i));
				}
				for (int i = 0; i < sortRecordList.size(); i++) {
					if("1".equals(segmentOfSubscriber) || "2".equals(segmentOfSubscriber)) {
						if (sortRecordList.get(i) == 2 || sortRecordList.get(i) == 3 || sortRecordList.get(i) == 4 || sortRecordList.get(i) == 5 ||
								sortRecordList.get(i) == 6 || sortRecordList.get(i) == 7 || sortRecordList.get(i) == 8 || sortRecordList.get(i) == 9) {
							// 不可エラーメッセージ
							ie.addErrMsgList(HimConstant.PROPERTY_FORMAT_ERROR, HimMsgConstant.MSG_HIM_W0147, String.valueOf(rowSubsNumber));
						}
				}
					rowSubsNumber++;
				}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：キー項目重複チェック</dd>
	 * <dd>メソッド説明：キー項目の重複がないかチェックします。</dd>
	 * </dl>
	 * @param eachlist
	 * @param checkKeyDatas
	 * @param errorList
	 * @param ie
	 */
	private static void checkKeyColumnOverlap(List<List<String>> eachlist, List<String> checkKeyDatas,
			List<MilInputErrorException> errorList, MilInputErrorException ie) {
		// キー情報は一番外側の変数に貯めておく。

		// キー項目重複チェック
		for (int i = 0; i < eachlist.size(); i++) {
			//種別削除の場合以外チェック
			if(!HimConstant.CODE_SUB_STATUS_CODE_NEW_RECORD_CATEGORY_DELETE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_PROCESS_CATEGORY))) {
				// 加入者基本情報変更履歴レコードのキー項目
				if (HimConstant.RECORD_TYPE_M_SUBSCRIBER_DATA_HISTORY_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

					// キー項目の比較用にカンマ区切りの文字列を設定する。
					StringBuilder sb = new StringBuilder();
					sb.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SUBSCRIBER_DATA_HISTORY_CHANGE_DATE)).append(";");

					// 同一のキー項目が複数設定されていないこと。
					if (!checkKeyDatas.isEmpty() && checkKeyDatas.contains(sb.toString())) {
						// 重複エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_M_SUBSCRIBER_DATA_HISTORY, HimMsgConstant.MSG_HIM_W0100);
					}
					// 現在のキー項目を保存。
					checkKeyDatas.add(sb.toString());
				}

				// 加入者資格情報レコードのキー項目
				if (HimConstant.RECORD_TYPE_M_QUALIFICATION_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

					// キー項目の比較用にカンマ区切りの文字列を設定する。
					StringBuilder sb = new StringBuilder();
					sb.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURER_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_PROOF_CODE)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_INSURED_PROOF_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_BRANCH_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_QUALIFICATION_QUALIFICATION_DATE)).append(";");

					// 同一のキー項目が複数設定されていないこと。
					if (!checkKeyDatas.isEmpty() && checkKeyDatas.contains(sb.toString())) {
						// 重複エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_QUAL_INFO, HimMsgConstant.MSG_HIM_W0107, eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_ID_NUMBER) +
								"," + HimConstant.RECORD_TYPE_M_QUALIFICATION_CODE);
					}
					// 現在のキー項目を保存。
					checkKeyDatas.add(sb.toString());
				}

				// 被保険者証等情報レコードのキー項目
				if (HimConstant.RECORD_TYPE_M_INSURED_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

					// キー項目の比較用にカンマ区切りの文字列を設定する。
					StringBuilder sb = new StringBuilder();
					sb.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_INSURED_PROOF_DIVISION)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_ISSUANCE_DATE)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_AVAILABLE_DATE)).append(";");

					// 同一のキー項目が複数設定されていないこと。
					if (!checkKeyDatas.isEmpty() && checkKeyDatas.contains(sb.toString())) {
						// 重複エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_INSURED_PROOF_INFO, HimMsgConstant.MSG_HIM_W0107,
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_ID_NUMBER) +
								"," + HimConstant.RECORD_TYPE_M_INSURED_PROOF_INFO_CODE);
					}
					// 現在のキー項目を保存。
					checkKeyDatas.add(sb.toString());
				}

				// 高齢受給者証情報レコードのキー項目
				if (HimConstant.RECORD_TYPE_M_ELDERLY_INSURED_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

					// キー項目の比較用にカンマ区切りの文字列を設定する。
					StringBuilder sb = new StringBuilder();
					sb.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_ISSUANCE_DATE)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_ELDERLY_AVAILABLE_DATE)).append(";");

					// 同一のキー項目が複数設定されていないこと。
					if (!checkKeyDatas.isEmpty() && checkKeyDatas.contains(sb.toString())) {
						// 重複エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_ELDERLY_INSURED_INFO, HimMsgConstant.MSG_HIM_W0107,
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_ID_NUMBER) +
								"," + HimConstant.RECORD_TYPE_M_ELDERLY_INSURED_PROOF_INFO_CODE);
					}
					// 現在のキー項目を保存。
					checkKeyDatas.add(sb.toString());
				}

				// 限度額適用認定証関連情報レコードのキー項目
				if (HimConstant.RECORD_TYPE_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

					// キー項目の比較用にカンマ区切りの文字列を設定する。
					StringBuilder sb = new StringBuilder();
					sb.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURED_PROOF_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_BRANCH_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_OF_COPAYMENT_PROOF_DIVISION)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_ISSUANCE_DATE)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_LIMIT_AVAILABLE_DATE)).append(";");

					// 同一のキー項目が複数設定されていないこと。
					if (!checkKeyDatas.isEmpty() && checkKeyDatas.contains(sb.toString())) {
						// 重複エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_LIMIT_OF_COPAYMENT_INFO, HimMsgConstant.MSG_HIM_W0107,
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_ID_NUMBER) +
								"," + HimConstant.RECORD_TYPE_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CODE);
					}
					// 現在のキー項目を保存。
					checkKeyDatas.add(sb.toString());
				}

				// 特定疾病療養受療証情報レコードのキー項目
				if (HimConstant.RECORD_TYPE_M_SPECIFIC_DISEASE_PROOF_INFO_CODE.equals(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY))) {

					// キー項目の比較用にカンマ区切りの文字列を設定する。
					StringBuilder sb = new StringBuilder();
					sb.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_CATEGORY)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURED_PROOF_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_BRANCH_NUMBER)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_SEGMENT_OF_DISEASE)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_ISSUANCE_DATE)).append(",")
					.append(eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_SPECIFIC_AVAILABLE_DATE)).append(";");

					// 同一のキー項目が複数設定されていないこと。
					if (!checkKeyDatas.isEmpty() && checkKeyDatas.contains(sb.toString())) {
						// 重複エラーメッセージ
						ie.addErrMsgList(HimConstant.PROPERTY_SPECIFIC_DISEASE_INFO, HimMsgConstant.MSG_HIM_W0107,
								eachlist.get(i).get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_RECORD_ID_NUMBER) +
								"," + HimConstant.RECORD_TYPE_M_SPECIFIC_DISEASE_PROOF_INFO_CODE);

					}
					// 現在のキー項目を保存。
					checkKeyDatas.add(sb.toString());
				}
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：レコード種別の並び順判定</dd>
	 * <dd>メソッド説明：レコード種別の並び順をチェックします。</dd>
	 * </dl>
	 * @param sortRecordList
	 * @param errorList
	 * @param ie
	 */
	private static void checkSortRecordCategory(List<Integer> sortRecordList, List<MilInputErrorException> errorList, MilInputErrorException ie) {

		// レコード種別の並び順判定
		for (int i = 0; i < sortRecordList.size() - 1; i++) {
			// 最後の要素の一つ前まで比較する
			if (sortRecordList.get(i) < sortRecordList.get(i + 1) || sortRecordList.get(i) == sortRecordList.get(i + 1)) {
				// 並び順OK
			} else {
				// レコード種別の並び順が正しくない場合は、エラーメッセージを出力する。
				ie.addErrMsgList(HimConstant.PROPERTY_RECORD_CATEGORY, HimMsgConstant.MSG_HIM_W0108);
			}
		}
	}

	/**
	 * <dl>
	 * <dd>メソッド名：資格喪失チェック。</dd>
	 * <dd>メソッド説明：個人番号と紐づく資格情報が喪失しているかチェックします。</dd>
	 * <dd>備考：共通処理</dd>
	 * </dl>
	 * @param insurerCode 保険者コード
	 * @param personalNumber 個人番号
	 * @param lineNumber 行番号
	 * @param requestFormatType 要求形式区分
	 * @param processId 処理ID
	 * @param em エンティティマネージャ
	 * @param sib システム共通項目
	 * @throws MilBusinessException
	 * @throws MilSystemException
	 */
	public static String checkDisqualifiedQual(String insurerCode, String personalNumber, SimpleDateFormat sdf) throws MilBusinessException, MilSystemException {
		// 資格喪失日
		String disqualDateStr = "";
		// 最新の資格喪失日
		String lastDisqualDate = "";

		// EntityManagerの生成
		String pun = SettingUtil.getCommonString(MilPropConstant.PERSISTENCE_UNIT_NAME);
		EntityManagerFactory emf = EntityManagerUtil.getInstance().getEntityManagerFactory(pun);
		EntityManager em = emf.createEntityManager();
		HimSubsLogic logic = new HimSubsLogic(em, new SystemInfoBean());
		// 個人番号をキーに加入者情報を取得
		SubsBean currentSubs = logic.getSubsDetailByPersonalNumber(insurerCode, personalNumber);

		if (currentSubs != null) {
			// 資格情報を取得
			List<QualBean> qualList = currentSubs.getQualList();

			for (int i = 0; i < qualList.size(); i++) {
				// 喪失した資格情報を判定する。
				if (qualList.get(i).getDisqualificationDate() != null) {
					disqualDateStr = sdf.format(qualList.get(i).getDisqualificationDate());
					if (disqualDateStr.compareTo(lastDisqualDate) > 0 || "".equals(lastDisqualDate)) {
						lastDisqualDate = disqualDateStr;
					}
				} else {
					lastDisqualDate = "";
					break;
				}
			}
		}
		return lastDisqualDate;
	}

	/**
	 * <dl>
	 * <dd>メソッド名：加入者区分の変更チェック。</dd>
	 * <dd>メソッド説明：個人番号と紐づく資格情報が喪失しているかチェックします。</dd>
	 * <dd>備考：共通処理</dd>
	 * </dl>
	 * @param insurerCode 保険者コード
	 * @param insuredBranchNumber 被保険者枝番
	 * @param segmentOfSubscriber
	 * @throws MilBusinessException
	 * @throws MilSystemException
	 */
	public static boolean checkSegmentChange(String insurerCode, String insuredBranchNumber,
			String segmentOfSubscriber, boolean conExist) throws MilBusinessException, MilSystemException {

		if(!"0".equals(segmentOfSubscriber)) {
			if(conExist) {
				// CSVに加入者基本情報以外の情報存在する
				return false;
			} else {
				// EntityManagerの生成
				String pun = SettingUtil.getCommonString(MilPropConstant.PERSISTENCE_UNIT_NAME);
				EntityManagerFactory emf = EntityManagerUtil.getInstance().getEntityManagerFactory(pun);
				EntityManager em = emf.createEntityManager();
				HimSubsLogic logic = new HimSubsLogic(em, new SystemInfoBean());
				// 保険者番号と被保険者枝番をキーに加入者情報を取得
				SubsBean currentSubs = logic.getSubsDetail(insurerCode, insuredBranchNumber);
				if (!(currentSubs.getMInfoServCtrlBean() == null && CheckUtil.isEmpty(currentSubs.getQualList()) && CheckUtil.isEmpty(currentSubs.getMSubscriberDataHistoryBeanList())
						&& CheckUtil.isEmpty(currentSubs.getMInsuredBeanList()) && CheckUtil.isEmpty(currentSubs.getMLimitOfCopaymentBeanList())
						&& CheckUtil.isEmpty(currentSubs.getMElderlyInsuredProofInfoBeanList()) && CheckUtil.isEmpty(currentSubs.getMSpecificDiseaseBeanList()))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * <dl>
	 * <dd>メソッド名：保険者番号存在チェック。</dd>
	 * <dd>メソッド説明：保険者番号の存在チェックを行います。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param insurerCode 保険者コード
	 * @param insurerNumber 保険者番号
	 * @param em エンティティマネージャー
	 * @param sib システム共通項目
	 * @return true/false 未登録の保険者番号が設定されている場合falseを返却する。
	 * @throws Exception 共通部品のシステムエラー。
	 */
	private static boolean checkInsurerNumberExists(String insurerCode, String insurerNumber, EntityManager em, SystemInfoBean sib)
		throws MilSystemException {

	    if (HimStringCheckUtil.checkEmpty(insurerCode) || HimStringCheckUtil.checkEmpty(insurerNumber)) {
	        // 検証対象の値が未設定の場合はtrueを返却する(エラーとしない)
	        return true;
	    }

        try {
            HimInsurerNumberLogic logic = new HimInsurerNumberLogic(em, sib);
    		if (logic.isInsurerNumberExists(insurerCode, insurerNumber)) {
    		    // 存在する場合trueを返却
    			return true;
    		}
        } catch (Exception e) {
            throw new MilSystemException(HimConstant.PROCESS_ID_BULK_FILE_SPLIT, MilDtlCodeConstant.SYS_ERROR_250001, "保険者番号マスタの検索中に例外が発生しました。", e);
        }

		// 存在しない場合falseを返却
		return false;
	}

    /**
	 * <dl>
	 * <dd>メソッド名：保険者番号(証)存在チェック。</dd>
	 * <dd>メソッド説明：保険者番号の存在チェックを行います。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param dataList 保険者コード
	 * @param lineNumber 処理中の行番号
	 * @param strFileLineNumber ファイル全体の行番号
	 * @param ie エラー格納オブジェクト
	 * @param em エンティティマネージャー
	 * @param sib システム共通項目
	 * @throws Exception 共通部品のシステムエラー。
	 */
    private static void checkExistsInsurerNumberInsuredProofInfo(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, EntityManager em, SystemInfoBean sib) throws MilSystemException {
		// 保険者コード
		String insurerCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE);
		// 保険者番号（証）
		String insurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER);
		if (!checkInsurerNumberExists(insurerCode, insurerNumber, em, sib)){
		    // 存在しない保険者番号を設定していた場合エラー
			ie.addErrMsgList(HimConstant.PROPERTY_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2134,
				strFileLineNumber, HimConstant.ITEM_CARD_INSURER_NUMBER);
		}
    }

    /**
	 * <dl>
	 * <dd>メソッド名：保険者番号(高齢受給者証)存在チェック。</dd>
	 * <dd>メソッド説明：保険者番号の存在チェックを行います。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param dataList 保険者コード
	 * @param lineNumber 処理中の行番号
	 * @param strFileLineNumber ファイル全体の行番号
	 * @param ie エラー格納オブジェクト
	 * @param em エンティティマネージャー
	 * @param sib システム共通項目
	 * @throws Exception 共通部品のシステムエラー。
	 */
    private static void checkExistsInsurerNumberElderlyInsuredProofInfo(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, EntityManager em, SystemInfoBean sib) throws MilSystemException {
        // 保険者コード
		String insurerCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE);
		// 保険者番号（高齢受給者証）
		String insurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER);
		if (!checkInsurerNumberExists(insurerCode, insurerNumber, em, sib)){
    		ie.addErrMsgList(HimConstant.PROPERTY_M_ELDERLY_INSURED_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2134,
    			strFileLineNumber, HimConstant.ITEM_ELDERLY_CARD_INSURER_NUMBER);
    	}
	}

    /**
	 * <dl>
	 * <dd>メソッド名：保険者番号(限度額認定証)存在チェック。</dd>
	 * <dd>メソッド説明：保険者番号の存在チェックを行います。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param dataList 保険者コード
	 * @param lineNumber 処理中の行番号
	 * @param strFileLineNumber ファイル全体の行番号
	 * @param ie エラー格納オブジェクト
	 * @param em エンティティマネージャー
	 * @param sib システム共通項目
	 * @throws Exception 共通部品のシステムエラー。
	 */
    private static void checkExistsInsurerNumberLimitOfCopayment(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, EntityManager em, SystemInfoBean sib) throws MilSystemException {
        // 保険者コード
		String insurerCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE);
		// 保険者番号（限度額認定証）
		String insurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER);
		if (!checkInsurerNumberExists(insurerCode, insurerNumber, em, sib)){
    		ie.addErrMsgList(HimConstant.PROPERTY_M_LIMIT_OF_COPAYMENT_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2134,
					strFileLineNumber, HimConstant.ITEM_LIMIT_CARD_INSURER_NUMBER);
    	}
	}

    /**
	 * <dl>
	 * <dd>メソッド名：保険者番号(特定疾病療養受療証)存在チェック。</dd>
	 * <dd>メソッド説明：保険者番号の存在チェックを行います。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param dataList 保険者コード
	 * @param lineNumber 処理中の行番号
	 * @param strFileLineNumber ファイル全体の行番号
	 * @param ie エラー格納オブジェクト
	 * @param em エンティティマネージャー
	 * @param sib システム共通項目
	 * @throws Exception 共通部品のシステムエラー。
	 */
    private static void checkExistsInsurerNumberSpecificDisease(List<String> dataList, int lineNumber, String strFileLineNumber,
			MilInputErrorException ie, EntityManager em, SystemInfoBean sib) throws MilSystemException {
        // 保険者コード
		String insurerCode = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURER_CODE);
		// 保険者番号（特定疾病療養受療証）
		String insurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER);
		if (!checkInsurerNumberExists(insurerCode, insurerNumber, em, sib)){
    		ie.addErrMsgList(HimConstant.PROPERTY_M_SPECIFIC_DISEASE_PROOF_INFO_CARD_INSURER_NUMBER, CmnMsgConstant.MSG_CMN_W2134,
					strFileLineNumber, HimConstant.ITEM_SPECIFIC_CARD_INSURER_NUMBER);
    	}
	}

    /**
	 * <dl>
	 * <dd>メソッド名：市町村コード存在チェック処理.</dd>
	 * <dd>メソッド説明：市町村コード存在チェックを行います。</dd>
	 * <dd>備考：</dd>
	 * </dl>
	 * @param muniCode 市町村コード
	 * @param em エンティティマネージャー
	 * @param sib システム共通項目
	 * @throws Exception 共通部品のシステムエラー。
	 */
    private static boolean checkMuniCodeExists(String muniCode, EntityManager em,  SystemInfoBean sib) throws MilSystemException {
         // 市町村コード存在チェックロジックを呼び出す
	    MuniCodeLogic muniCodelogic = new MuniCodeLogic(em , sib);
	    return muniCodelogic.isMuniCodeExists(muniCode);
    }

    /**
 	 * <dl>
 	 * <dd>メソッド名：被保険者証情報チェック処理.</dd>
 	 * <dd>メソッド説明：被保険者情報の重複チェックを行います。</dd>
 	 * <dd>備考：</dd>
 	 * </dl>
 	 * @param dataList 保険者情報
 	 * @param strFileLineNumber ファイル全体の行番号
 	 * @param ie エラー格納オブジェクト
 	 * @param em エンティティマネージャー
 	 * @param sib システム共通項目
 	 */
    private static void checkDuplicateInsuredProofInfo(List<String> dataList,String strFileLineNumber, MilInputErrorException ie, EntityManager em, SystemInfoBean sib) {

    	// 被保険者枝番をdataListから取得する
    	String inputInsuredBranchNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_NEW_INSURED_BRANCH_NUMBER);
    	// 保険者番号、被保険者証記号、被保険者証番号、被保険者証枝番をdataListから取得する
    	String cardInsurerNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURER_NUMBER);
    	String cardInsuredProofSymbol = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_SYMBOL);
    	String cardInsuredProofNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_INSURED_PROOF_NUMBER);
    	String cardBranchNumber = dataList.get(HimConstant.SUBS_REGIST_FILE_COL_NUM_M_INSURED_PROOF_INFO_CARD_BRANCH_NUMBER);

    	// 被保険者証等情報beanに保険者番号、被保険者証記号、被保険者証番号、被保険者証枝番を格納する。
    	MInsuredProofInfoInputBean insuredProofInfoInputBean = new MInsuredProofInfoInputBean();
    	insuredProofInfoInputBean.setCardInsurerNumber(cardInsurerNumber);
    	insuredProofInfoInputBean.setCardinsuredProofSymbol(cardInsuredProofSymbol);
    	insuredProofInfoInputBean.setCardInsuredProofNumber(cardInsuredProofNumber);
    	insuredProofInfoInputBean.setCardBranchNumber(cardBranchNumber);

    	// 被保険者証情報の重複チェックを行う
		MInsuredProofInfoLogic insuredProofInfoLogic = new MInsuredProofInfoLogic(em,sib);
		String insuredBranchNumber = insuredProofInfoLogic.isInsuredProofInfoExists(insuredProofInfoInputBean);
		if(!HimStringCheckUtil.checkEmpty(insuredBranchNumber) &&
				!insuredBranchNumber.equals(inputInsuredBranchNumber)) {
			ie.addErrMsgList(HimConstant.PROPERTY_CARD_INSURER_NUMBER, HimMsgConstant.MSG_HIM_W0111,
					strFileLineNumber,insuredBranchNumber);
			ie.setDetailCode(HimDtlCodeConstant.BUS_ERROR_11G107);
		}
    }
}