package com.changyo.core.actuator;

import static com.changyo.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static com.changyo.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static com.changyo.core.actuator.ActuatorConstant.WITNESS_EXCEPTION_STR;

import com.changyo.core.Wallet;
import com.changyo.core.capsule.ProposalCapsule;
import com.changyo.core.capsule.TransactionResultCapsule;
import com.changyo.core.config.Parameter;
import com.changyo.core.config.args.Args;
import com.changyo.core.db.Manager;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import com.changyo.common.utils.StringUtil;
import com.changyo.core.exception.ContractExeException;
import com.changyo.core.exception.ContractValidateException;
import com.changyo.protos.Contract.ProposalCreateContract;
import com.changyo.protos.Protocol.Transaction.Result.code;
import com.changyo.common.utils.ByteArray;
@Slf4j(topic = "actuator")
public class ProposalCreateActuator extends AbstractActuator {

  ProposalCreateActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final ProposalCreateContract proposalCreateContract = this.contract
          .unpack(ProposalCreateContract.class);
      long id = (Objects.isNull(getDeposit())) ?
          dbManager.getDynamicPropertiesStore().getLatestProposalNum() + 1 :
          getDeposit().getLatestProposalNum() + 1;
      ProposalCapsule proposalCapsule =
          new ProposalCapsule(proposalCreateContract.getOwnerAddress(), id);

      proposalCapsule.setParameters(proposalCreateContract.getParametersMap());

      long now = dbManager.getHeadBlockTimeStamp();
      long maintenanceTimeInterval = (Objects.isNull(getDeposit())) ?
          dbManager.getDynamicPropertiesStore().getMaintenanceTimeInterval() :
          getDeposit().getMaintenanceTimeInterval();
      proposalCapsule.setCreateTime(now);

      long currentMaintenanceTime =
          (Objects.isNull(getDeposit())) ? dbManager.getDynamicPropertiesStore()
              .getNextMaintenanceTime() :
              getDeposit().getNextMaintenanceTime();
      long now3 = now + Args.getInstance().getProposalExpireTime();
      long round = (now3 - currentMaintenanceTime) / maintenanceTimeInterval;
      long expirationTime =
          currentMaintenanceTime + (round + 1) * maintenanceTimeInterval;
      proposalCapsule.setExpirationTime(expirationTime);

      if (Objects.isNull(deposit)) {
        dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
        dbManager.getDynamicPropertiesStore().saveLatestProposalNum(id);
      } else {
        deposit.putProposalValue(proposalCapsule.createDbKey(), proposalCapsule);
        deposit.putDynamicPropertiesWithLatestProposalNum(id);
      }

      ret.setStatus(fee, code.SUCESS);
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
    if (dbManager == null && (deposit == null || deposit.getDbManager() == null)) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(ProposalCreateContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [ProposalCreateContract],real type[" + contract
              .getClass() + "]");
    }
    final ProposalCreateContract contract;
    try {
      contract = this.contract.unpack(ProposalCreateContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    if (!Objects.isNull(deposit)) {
      if (Objects.isNull(deposit.getAccount(ownerAddress))) {
        throw new ContractValidateException(
            ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }
    } else if (!dbManager.getAccountStore().has(ownerAddress)) {
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    if (!Objects.isNull(getDeposit())) {
      if (Objects.isNull(getDeposit().getWitness(ownerAddress))) {
        throw new ContractValidateException(
            WITNESS_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }
    } else if (!dbManager.getWitnessStore().has(ownerAddress)) {
      throw new ContractValidateException(
          WITNESS_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    if (contract.getParametersMap().size() == 0) {
      throw new ContractValidateException("This proposal has no parameter.");
    }

    for (Map.Entry<Long, String> entry : contract.getParametersMap().entrySet()) {
      if (!validKey(entry.getKey())) {
        throw new ContractValidateException("Bad chain parameter id");
      }
      validateValue(entry);
    }

    return true;
  }

  private void validateValue(Map.Entry<Long, String> entry) throws ContractValidateException {

    switch (entry.getKey().intValue()) {
      case (0): {
        if (Long.parseLong(entry.getValue()) < 3 * 27 * 1000 || Long.parseLong(entry.getValue()) > 24 * 3600 * 1000) {
          throw new ContractValidateException(
                  "Bad chain parameter value,valid range is [3 * 27 * 1000,24 * 3600 * 1000]");
        }
        return;
      }
      case (1):
      case (2):
      case (3):
      case (4):
      case (5):
      case (6):
      case (7):
      case (8): {
        if (Long.parseLong(entry.getValue()) < 0 || Long.parseLong(entry.getValue()) > 100_000_000_000_000_000L) {
          throw new ContractValidateException(
                  "Bad chain parameter value,valid range is [0,100_000_000_000_000_000L]");
        }
        break;
      }
      case (9): {
        if (Long.parseLong(entry.getValue()) != 1) {
          throw new ContractValidateException(
                  "This value[ALLOW_CREATION_OF_CONTRACTS] is only allowed to be 1");
        }
        break;
      }
      case (10): {
        if (dbManager.getDynamicPropertiesStore().getRemoveThePowerOfTheGr() == -1) {
          throw new ContractValidateException(
                  "This proposal has been executed before and is only allowed to be executed once");
        }

        if (Long.parseLong(entry.getValue()) != 1) {
          throw new ContractValidateException(
                  "This value[REMOVE_THE_POWER_OF_THE_GR] is only allowed to be 1");
        }
        break;
      }
      case (11):
        break;
      case (12):
        break;
      case (13):
        if (Long.parseLong(entry.getValue()) < 10 || Long.parseLong(entry.getValue()) > 100) {
          throw new ContractValidateException(
                  "Bad chain parameter value,valid range is [10,100]");
        }
        break;
      case (14): {
        if (Long.parseLong(entry.getValue()) != 1) {
          throw new ContractValidateException(
                  "This value[ALLOW_UPDATE_ACCOUNT_NAME] is only allowed to be 1");
        }
        break;
      }
      case (15): {
        if (Long.parseLong(entry.getValue()) != 1) {
          throw new ContractValidateException(
                  "This value[ALLOW_SAME_TOKEN_NAME] is only allowed to be 1");
        }
        break;
      }
      case (16): {
        if (Long.parseLong(entry.getValue()) != 1) {
          throw new ContractValidateException(
                  "This value[ALLOW_DELEGATE_RESOURCE] is only allowed to be 1");
        }
        break;
      }
      case (17): { // deprecated
        if (!dbManager.getForkController().pass(Parameter.ForkBlockVersionConsts.ENERGY_LIMIT)) {
          throw new ContractValidateException("Bad chain parameter id");
        }
        if (dbManager.getForkController().pass(Parameter.ForkBlockVersionEnum.VERSION_3_2_2)) {
          throw new ContractValidateException("Bad chain parameter id");
        }
        if (Long.parseLong(entry.getValue()) < 0 || Long.parseLong(entry.getValue()) > 100_000_000_000_000_000L) {
          throw new ContractValidateException(
                  "Bad chain parameter value,valid range is [0,100_000_000_000_000_000L]");
        }
        break;
      }
      case (18): {
        if (Long.parseLong(entry.getValue()) != 1) {
          throw new ContractValidateException(
                  "This value[ALLOW_TVM_TRANSFER_TRC10] is only allowed to be 1");
        }
        if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
          throw new ContractValidateException("[ALLOW_SAME_TOKEN_NAME] proposal must be approved "
                  + "before [ALLOW_TVM_TRANSFER_TRC10] can be proposed");
        }
        break;
      }
      case (19): {
        if (!dbManager.getForkController().pass(Parameter.ForkBlockVersionEnum.VERSION_3_2_2)) {
          throw new ContractValidateException("Bad chain parameter id");
        }
        if (Long.parseLong(entry.getValue()) < 0 || Long.parseLong(entry.getValue()) > 100_000_000_000_000_000L) {
          throw new ContractValidateException(
                  "Bad chain parameter value,valid range is [0,100_000_000_000_000_000L]");
        }
        break;
      }
      case (20): {
        if (!dbManager.getForkController().pass(Parameter.ForkBlockVersionEnum.VERSION_3_5)) {
          throw new ContractValidateException("Bad chain parameter id: ALLOW_MULTI_SIGN");
        }
        if (Long.parseLong(entry.getValue()) != 1) {
          throw new ContractValidateException(
                  "This value[ALLOW_MULTI_SIGN] is only allowed to be 1");
        }
        break;
      }
      case (21): {
        if (!dbManager.getForkController().pass(Parameter.ForkBlockVersionEnum.VERSION_3_5)) {
          throw new ContractValidateException("Bad chain parameter id: ALLOW_ADAPTIVE_ENERGY");
        }
        if (Long.parseLong(entry.getValue()) != 1) {
          throw new ContractValidateException(
                  "This value[ALLOW_ADAPTIVE_ENERGY] is only allowed to be 1");
        }
        break;
      }
      case (22): {
        if (!dbManager.getForkController().pass(Parameter.ForkBlockVersionEnum.VERSION_3_5)) {
          throw new ContractValidateException(
                  "Bad chain parameter id: UPDATE_ACCOUNT_PERMISSION_FEE");
        }
        if (Long.parseLong(entry.getValue()) < 0 || Long.parseLong(entry.getValue()) > 100_000_000_000L) {
          throw new ContractValidateException(
                  "Bad chain parameter value,valid range is [0,100_000_000_000L]");
        }
        break;
      }
      case (23): {
        if (!dbManager.getForkController().pass(Parameter.ForkBlockVersionEnum.VERSION_3_5)) {
          throw new ContractValidateException("Bad chain parameter id: MULTI_SIGN_FEE");
        }
        if (Long.parseLong(entry.getValue()) < 0 || Long.parseLong(entry.getValue()) > 100_000_000_000L) {
          throw new ContractValidateException(
                  "Bad chain parameter value,valid range is [0,100_000_000_000L]");
        }
        break;
      }
      case (24):
      case (25): {

          byte[] newSupplyAddress = ByteArray.fromHexString(entry.getValue());
          String readableAddress = StringUtil.createReadableString(newSupplyAddress);

          if (!Wallet.addressValid(newSupplyAddress)) {
            throw new ContractValidateException("Invalid supply address");
          }

          if (!Objects.isNull(deposit)) {
            if (Objects.isNull(deposit.getAccount(newSupplyAddress))) {
              throw new ContractValidateException(
                      ACCOUNT_EXCEPTION_STR + readableAddress + NOT_EXIST_STR);
            }
          } else if (!dbManager.getAccountStore().has(newSupplyAddress)) {
            throw new ContractValidateException(
                    ACCOUNT_EXCEPTION_STR + readableAddress + NOT_EXIST_STR);
          }
        }
        break;
        default:
          break;
      }
    }
  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ProposalCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

  private boolean validKey(long idx) {
    return idx >= 0 && idx < Parameter.ChainParameters.values().length;
  }

}
