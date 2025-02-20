package br.com.uol.pagseguro.smartcoffee.payments.demoInterno;

import com.hannesdorfmann.mosby.mvp.MvpNullObjectBasePresenter;

import javax.inject.Inject;

import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPag;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagEventData;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagTransactionResult;
import br.com.uol.pagseguro.smartcoffee.ActionResult;
import br.com.uol.pagseguro.smartcoffee.payments.PaymentsUseCase;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class DemoInternoPresenter extends MvpNullObjectBasePresenter<DemoInternoContract> {

    private final PaymentsUseCase mUseCase;
    private Disposable mSubscribe;
    private int countPassword = 0;

    @Inject
    public DemoInternoPresenter(PaymentsUseCase useCase) {
        mUseCase = useCase;
    }

    public void pixPayment(int value) {
        doAction(mUseCase.doPixPayment(value), value);
    }

    public void doDebitPayment(int value) {
        doAction(mUseCase.doDebitPayment(value, false), value);
    }

    public void doDebitCarnePayment(int value) {
        doAction(mUseCase.doDebitPayment(value, true), value);
    }

    public void creditCarnePayment(int value) {
        doAction(mUseCase.doCreditPayment(value, true), value);
    }

    public void doVoucherPayment(int value) {
        doAction(mUseCase.doVoucherPayment(value), value);
    }

    public void doRefund(ActionResult actionResult) {
        if (actionResult.getMessage() != null) {
            getView().showTransactionDialog(actionResult.getMessage());
            getView().disposeDialog();
        } else {
            doAction(mUseCase.doRefund(actionResult), 0);
        }
    }

    public void doRefundQrCode(ActionResult actionResult) {
        if (actionResult.getMessage() != null) {
            getView().showTransactionDialog(actionResult.getMessage());
            getView().disposeDialog();
        } else {
            doAction(mUseCase.doRefundQrCode(actionResult), 0);
        }
    }

    private void doAction(Observable<ActionResult> action, int value) {
        mSubscribe = action
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(() -> getView().showTransactionSuccess())
                .doOnDispose(() -> getView().disposeDialog())
                .subscribe((ActionResult result) -> {
                            writeToFile(result);
                            updateValue(result.getTransactionResult());

                            if (result.getEventCode() == PlugPagEventData.EVENT_CODE_NO_PASSWORD ||
                                    result.getEventCode() == PlugPagEventData.EVENT_CODE_DIGIT_PASSWORD) {
                                getView().showTransactionDialog(checkMessagePassword(result.getEventCode(), value));
                            } else {
                                getView().showTransactionDialog(checkMessage(result.getMessage()));
                            }
                        },
                        throwable -> {
                            getView().showTransactionDialog(throwable.getMessage());
                            getView().disposeDialog();
                        });
    }

    private String checkMessagePassword(int eventCode, int value) {
        StringBuilder strPassword = new StringBuilder();

        if (eventCode == PlugPagEventData.EVENT_CODE_DIGIT_PASSWORD) {
            countPassword++;
        }
        if (eventCode == PlugPagEventData.EVENT_CODE_NO_PASSWORD) {
            countPassword = 0;
        }

        for (int count = countPassword; count > 0; count--) {
            strPassword.append("*");
        }

        return String.format("VALOR: %.2f\nSENHA: %s", (value / 100.0), strPassword);
    }

    private String checkMessage(String message) {
        if (message != null && message.contains("SENHA")) {
            String[] strings = message.split("SENHA");
            return strings[0].trim();
        }

        return message;
    }

    private void writeToFile(ActionResult result) {
        if (result.getTransactionCode() != null && result.getTransactionId() != null) {
            getView().writeToFile(result.getTransactionCode(), result.getTransactionId());
        }
    }

    private void updateValue(PlugPagTransactionResult result) {
        if (result != null && result.getResult() == PlugPag.RET_OK &&
                result.getPartialPayRemainingAmount() != null &&
                !result.getPartialPayRemainingAmount().isEmpty()) {
            getView().setPaymentValue(result.getPartialPayRemainingAmount());
        }
    }

    public void abortTransaction() {
        mSubscribe = mUseCase.abort()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    @Override
    public void detachView(boolean retainInstance) {
        if (mSubscribe != null) {
            mSubscribe.dispose();
        }
        super.detachView(retainInstance);
    }

    public void getLastTransaction() {
        mSubscribe = mUseCase.getLastTransaction()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(actionResult -> getView().showTransactionDialog(actionResult.getTransactionCode()),
                        throwable -> getView().showTransactionDialog(throwable.getMessage()));
    }
}
