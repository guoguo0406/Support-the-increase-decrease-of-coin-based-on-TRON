package com.changyo.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import com.changyo.common.utils.StringUtil;
import com.changyo.core.Wallet;
import com.changyo.core.capsule.AccountCapsule;
import com.changyo.core.capsule.TransactionResultCapsule;
import com.changyo.core.db.Manager;
import com.changyo.core.exception.BalanceInsufficientException;
import com.changyo.core.exception.ContractExeException;
import com.changyo.core.exception.ContractValidateException;
import com.changyo.protos.Contract.ModifySupplyContract;
import com.changyo.protos.Protocol;
import com.changyo.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ModifySupplyActuator extends AbstractActuator {

  ModifySupplyActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret)
      throws ContractExeException {
    long fee = calcFee();
    try {
      ModifySupplyContract modifySupplyContract = contract.unpack(ModifySupplyContract.class);

      //AccountCapsule accountOwnerCapsule = dbManager.getAccountStore()
      //        .get(modifySupplyContract.getOwnerAddress().toByteArray());

      AccountCapsule accountCustomerCapsule = dbManager.getAccountStore()
              .get(modifySupplyContract.getCustomerAddress().toByteArray());

      if (accountCustomerCapsule == null) {
        boolean withDefaultPermission =
                dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        accountCustomerCapsule = new AccountCapsule(ByteString.copyFrom(modifySupplyContract.getCustomerAddress().toByteArray()), Protocol.AccountType.Normal,
                dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(modifySupplyContract.getCustomerAddress().toByteArray(), accountCustomerCapsule);
        fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }

      long amount = modifySupplyContract.getAmount();
      boolean is_increase = modifySupplyContract.getIsIncrease();
      long totalSupply = dbManager.getDynamicPropertiesStore().getTotalSupply();

      logger.debug("ModifySupplyActuator execute begin totalSupply: {}, customer balance: {}", totalSupply, accountCustomerCapsule.getBalance());

      dbManager.adjustBalance(modifySupplyContract.getOwnerAddress().toByteArray(), -fee);
      dbManager.adjustBalance(dbManager.getAccountStore().getBlackhole().createDbKey(), fee);

      if (is_increase) {
        dbManager.adjustBalance(modifySupplyContract.getCustomerAddress().toByteArray(), amount);
        totalSupply += amount;
      }
      else {
        dbManager.adjustBalance(modifySupplyContract.getCustomerAddress().toByteArray(), -amount);
        totalSupply -= amount;
      }

      dbManager.getDynamicPropertiesStore().saveTotalSupply(totalSupply);
      logger.debug("ModifySupplyActuator execute begin totalSupply: {}, customer balance: {}", totalSupply, accountCustomerCapsule.getBalance());
      ret.setStatus(fee, code.SUCESS);
    } catch (BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(ModifySupplyContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [ModifySupplyContract],real type[" + contract
              .getClass() + "]");
    }
    final ModifySupplyContract contract;
    try {
      contract = this.contract.unpack(ModifySupplyContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid ownerAddress");
    }

    AccountCapsule accountOwnerCapsule = dbManager.getAccountStore().get(ownerAddress);
    if (accountOwnerCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          "Account[" + readableOwnerAddress + "] not exists");
    }
    boolean is_increase = contract.getIsIncrease();
    if (is_increase) {
      if (!(Arrays.equals(dbManager.getDynamicPropertiesStore().getIncreaseSupplyAddress(), ownerAddress))) {
        throw new ContractValidateException("Cannot increase the totalsupply except the increase address.");
      }
    }
    else {
      if (!(Arrays.equals(dbManager.getDynamicPropertiesStore().getDecreaseSupplyAddress(), ownerAddress))) {
        throw new ContractValidateException("Cannot decrease the totalsupply except the decrease address.");
      }
    }

    byte[] customerAddress = contract.getCustomerAddress().toByteArray();
    if (!Wallet.addressValid(customerAddress)) {
      throw new ContractValidateException("Invalid customerAddress");
    }

    long fee = calcFee();

    AccountCapsule accountCustomerCapsule = dbManager.getAccountStore().get(customerAddress);
    if (accountCustomerCapsule == null) {
      if (is_increase) {
        fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }
      else{
        String readableCustomerAddress = Wallet.encode58Check(customerAddress);
        throw new ContractValidateException(
                "DecreaseSupply the account[" + readableCustomerAddress + "] not exists");
      }

    }
    if (accountOwnerCapsule.getBalance() < fee) {
      throw new ContractValidateException(
    "Validate ModifySupplyContract error, insufficient fee.");
  }

    long amount = contract.getAmount();

    if (amount <= 0) {
      throw new ContractValidateException("Amount must greater than 0.");
    }

    if (!is_increase) {
      if (accountCustomerCapsule.getBalance() < amount){
        throw new ContractValidateException(
                "Validate ModifySupplyContract error, insufficient balance.");
      }
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ModifySupplyContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
