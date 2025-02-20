package br.com.uol.pagseguro.smartcoffee.payments.qrcode;

import static br.com.uol.pagseguro.smartcoffee.utils.InstallmentConstants.TRANSACTION_TYPE;
import static br.com.uol.pagseguro.smartcoffee.utils.SmartCoffeeConstants.INSTALLMENT_TYPE_PARC_COMPRADOR;
import static br.com.uol.pagseguro.smartcoffee.utils.SmartCoffeeConstants.INSTALLMENT_TYPE_PARC_VENDEDOR;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.hannesdorfmann.mosby.mvp.MvpActivity;

import javax.inject.Inject;

import br.com.uol.pagseguro.smartcoffee.R;
import br.com.uol.pagseguro.smartcoffee.databinding.ActivityQrcodeOptionsBinding;
import br.com.uol.pagseguro.smartcoffee.injection.DaggerQrcodeComponent;
import br.com.uol.pagseguro.smartcoffee.injection.QrcodeComponent;
import br.com.uol.pagseguro.smartcoffee.injection.UseCaseModule;
import br.com.uol.pagseguro.smartcoffee.injection.WrapperModule;
import br.com.uol.pagseguro.smartcoffee.payments.demoInterno.CustomDialog;
import br.com.uol.pagseguro.smartcoffee.payments.installments.SelectInstallmentActivity;
import br.com.uol.pagseguro.smartcoffee.utils.FileHelper;
import br.com.uol.pagseguro.smartcoffee.utils.InstallmentConstants;
import br.com.uol.pagseguro.smartcoffee.utils.UIFeedback;

public class QrcodeActivity extends MvpActivity<QrcodeContract, QrcodePresenter> implements QrcodeContract {

    private int value;
    CustomDialog dialog;

    public static final String TAG = "valueQR";
    public static final int UNKNOW_AMOUNT = 0;
    public static final int UNKNOW_TRANSACTION_TYPE = 0;
    private static final int VALUE_MINIMAL_INSTALLMENT = 1000;
    private static final int LAUNCH_SECOND_ACTIVITY = 1;

    private boolean shouldShowDialog;
    private boolean mCanClick = true;

    @Inject
    QrcodeComponent mInjector;
    private ActivityQrcodeOptionsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mInjector = DaggerQrcodeComponent.builder()
                .useCaseModule(new UseCaseModule())
                .wrapperModule(new WrapperModule(getApplicationContext()))
                .build();
        mInjector.inject(this);
        super.onCreate(savedInstanceState);

        binding = ActivityQrcodeOptionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getExtra(getIntent().getExtras());

        clickButtons();
    }

    @NonNull
    @Override
    public QrcodePresenter createPresenter() {
        return mInjector.presenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LAUNCH_SECOND_ACTIVITY) {
            if (resultCode == Activity.RESULT_OK) {
                final int amount = data.getIntExtra(
                        InstallmentConstants.TOTAL_VALUE,
                        UNKNOW_AMOUNT
                );
                final int installmentNumber = data.getIntExtra(
                        InstallmentConstants.INSTALLMENT_NUMBER,
                        0
                );
                final int transactionType = data.getIntExtra(
                        TRANSACTION_TYPE,
                        UNKNOW_TRANSACTION_TYPE
                );

                switch (transactionType) {
                    case INSTALLMENT_TYPE_PARC_COMPRADOR:
                        getPresenter().qrCodePaymentBuyerInstallments(
                                amount, installmentNumber
                        );
                        break;
                    case INSTALLMENT_TYPE_PARC_VENDEDOR:
                        getPresenter().qrCodePaymentSellerInstallments(
                                amount, installmentNumber
                        );
                        break;
                    default:
                        UIFeedback.showDialog(this, getString(R.string.text_invalid_operation));
                        break;
                }
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                mCanClick = true;
                UIFeedback.showDialog(this, R.string.text_not_selected_installment);
            }
        }
    }

    private void clickButtons() {
        dialog = new CustomDialog(this);
        dialog.setOnCancelListener(cancelListener);

        binding.btnQRCodeInCashDebit.setOnClickListener(click -> {
            if (!mCanClick) {
                return;
            }
            mCanClick = false;
            shouldShowDialog = true;
            getPresenter().qrCodePaymentInCashDebit(value);
        });
        binding.btnQRCodeInCashCredit.setOnClickListener(click -> {
            if (!mCanClick) {
                return;
            }
            mCanClick = false;
            shouldShowDialog = true;
            getPresenter().qrCodePaymentInCashCredit(value);
        });
        binding.btnQRCodeBuyerInstallments.setOnClickListener(click -> {
            if (!mCanClick) {
                return;
            }
            mCanClick = false;
            shouldShowDialog = true;
            if (value < VALUE_MINIMAL_INSTALLMENT) {
                showTransactionDialog(getString(R.string.txt_installments_invalid_message));
            } else {
                Intent intent = SelectInstallmentActivity.getStartIntent(
                        getApplicationContext(),
                        value,
                        INSTALLMENT_TYPE_PARC_COMPRADOR
                );
                startActivityForResult(intent, LAUNCH_SECOND_ACTIVITY);
            }
        });
        binding.btnQRCodeSellerInstallments.setOnClickListener(click -> {
            if (!mCanClick) {
                return;
            }
            mCanClick = false;
            shouldShowDialog = true;
            if (value < VALUE_MINIMAL_INSTALLMENT) {
                showTransactionDialog(getString(R.string.txt_installments_invalid_message));
            } else {
                Intent intent = SelectInstallmentActivity.getStartIntent(
                        getApplicationContext(),
                        value,
                        INSTALLMENT_TYPE_PARC_VENDEDOR
                );
                startActivityForResult(intent, LAUNCH_SECOND_ACTIVITY);
            }
        });
    }

    private void getExtra(Bundle extras) {
        if (extras != null) {
            value = extras.getInt(TAG);
        } else {
            value = 0;
        }
    }

    DialogInterface.OnCancelListener cancelListener = dialogInterface -> {
        dialogInterface.dismiss();
        if (shouldShowDialog) {
            getPresenter().abortTransaction();
        }
    };

    @Override
    public void showTransactionSuccess() {
        mCanClick = true;
        showTransactionDialog(getString(R.string.transactions_successful));
    }

    @Override
    public void showTransactionDialog(String message) {
        if (shouldShowDialog && !dialog.isShowing()) {
            dialog.show();
        }
        dialog.setMessage(message);
    }

    @Override
    public void showLoading(boolean show) {
        if (show) {
            UIFeedback.showProgress(this);
        } else {
            UIFeedback.dismissProgress();
            UIFeedback.releaseVariables();
        }
    }

    @Override
    public void writeToFile(String transactionCode, String transactionId) {
        FileHelper.writeToFile(transactionCode, transactionId, this);
    }

    @Override
    public void disposeDialog() {
        mCanClick = true;
        shouldShowDialog = false;
    }

}