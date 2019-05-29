package com.changyo.core.witness;

import com.changyo.core.capsule.ProposalCapsule;
import com.changyo.core.db.Manager;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import com.changyo.common.utils.ByteArray;
import com.changyo.protos.Protocol.Proposal.State;

@Slf4j(topic = "witness")
public class ProposalController {

  @Setter
  @Getter
  private Manager manager;

  public static ProposalController createInstance(Manager manager) {
    ProposalController instance = new ProposalController();
    instance.setManager(manager);
    return instance;
  }


  public void processProposals() {
    long latestProposalNum = manager.getDynamicPropertiesStore().getLatestProposalNum();
    if (latestProposalNum == 0) {
      logger.info("latestProposalNum is 0,return");
      return;
    }

    long proposalNum = latestProposalNum;

    ProposalCapsule proposalCapsule = null;

    while (proposalNum > 0) {
      try {
        proposalCapsule = manager.getProposalStore()
            .get(ProposalCapsule.calculateDbKey(proposalNum));
      } catch (Exception ex) {
        logger.error("", ex);
        continue;
      }

      if (proposalCapsule.hasProcessed()) {
        logger
            .info("Proposal has processed，id:[{}],skip it and before it",
                proposalCapsule.getID());
        //proposals with number less than this one, have been processed before
        break;
      }

      if (proposalCapsule.hasCanceled()) {
        logger.info("Proposal has canceled，id:[{}],skip it", proposalCapsule.getID());
        proposalNum--;
        continue;
      }

      long currentTime = manager.getDynamicPropertiesStore().getNextMaintenanceTime();
      if (proposalCapsule.hasExpired(currentTime)) {
        processProposal(proposalCapsule);
        proposalNum--;
        continue;
      }

      proposalNum--;
      logger.info("Proposal has not expired，id:[{}],skip it", proposalCapsule.getID());
    }
    logger.info("Processing proposals done, oldest proposal[{}]", proposalNum);
  }

  public void processProposal(ProposalCapsule proposalCapsule) {

    List<ByteString> activeWitnesses = this.manager.getWitnessScheduleStore().getActiveWitnesses();
    if (proposalCapsule.hasMostApprovals(activeWitnesses)) {
      logger.info(
          "Processing proposal,id:{},it has received most approvals, "
              + "begin to set dynamic parameter:{}, "
              + "and set proposal state as APPROVED",
          proposalCapsule.getID(), proposalCapsule.getParameters());
      setDynamicParameters(proposalCapsule);
      proposalCapsule.setState(State.APPROVED);
      manager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
    } else {
      logger.info(
          "Processing proposal,id:{}, "
              + "it has not received enough approvals, set proposal state as DISAPPROVED",
          proposalCapsule.getID());
      proposalCapsule.setState(State.DISAPPROVED);
      manager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
    }

  }

  public void setDynamicParameters(ProposalCapsule proposalCapsule) {
    Map<Long, String> map = proposalCapsule.getInstance().getParametersMap();
    for (Map.Entry<Long, String> entry : map.entrySet()) {

      switch (entry.getKey().intValue()) {
        case (0): {
          manager.getDynamicPropertiesStore().saveMaintenanceTimeInterval(Long.parseLong(entry.getValue()));
          break;
        }
        case (1): {
          manager.getDynamicPropertiesStore().saveAccountUpgradeCost(Long.parseLong(entry.getValue()));
          break;
        }
        case (2): {
          manager.getDynamicPropertiesStore().saveCreateAccountFee(Long.parseLong(entry.getValue()));
          break;
        }
        case (3): {
          manager.getDynamicPropertiesStore().saveTransactionFee(Long.parseLong(entry.getValue()));
          break;
        }
        case (4): {
          manager.getDynamicPropertiesStore().saveAssetIssueFee(Long.parseLong(entry.getValue()));
          break;
        }
        case (5): {
          manager.getDynamicPropertiesStore().saveWitnessPayPerBlock(Long.parseLong(entry.getValue()));
          break;
        }
        case (6): {
          manager.getDynamicPropertiesStore().saveWitnessStandbyAllowance(Long.parseLong(entry.getValue()));
          break;
        }
        case (7): {
          manager.getDynamicPropertiesStore()
              .saveCreateNewAccountFeeInSystemContract(Long.parseLong(entry.getValue()));
          break;
        }
        case (8): {
          manager.getDynamicPropertiesStore().saveCreateNewAccountGasRate(Long.parseLong(entry.getValue()));
          break;
        }
        case (9): {
          manager.getDynamicPropertiesStore().saveAllowCreationOfContracts(Long.parseLong(entry.getValue()));
          break;
        }
        case (10): {
          if (manager.getDynamicPropertiesStore().getRemoveThePowerOfTheGr() == 0) {
            manager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(Long.parseLong(entry.getValue()));
          }
          break;
        }
        case (11): {
          manager.getDynamicPropertiesStore().saveEnergyFee(Long.parseLong(entry.getValue()));
          break;
        }
        case (12): {
          manager.getDynamicPropertiesStore().saveExchangeCreateFee(Long.parseLong(entry.getValue()));
          break;
        }
        case (13): {
          manager.getDynamicPropertiesStore().saveMaxCpuTimeOfOneTx(Long.parseLong(entry.getValue()));
          break;
        }
        case (14): {
          manager.getDynamicPropertiesStore().saveAllowUpdateAccountName(Long.parseLong(entry.getValue()));
          break;
        }
        case (15): {
          manager.getDynamicPropertiesStore().saveAllowSameTokenName(Long.parseLong(entry.getValue()));
          break;
        }
        case (16): {
          manager.getDynamicPropertiesStore().saveAllowDelegateResource(Long.parseLong(entry.getValue()));
          break;
        }
        case (17): {
          manager.getDynamicPropertiesStore().saveTotalEnergyLimit(Long.parseLong(entry.getValue()));
          break;
        }
        case (18): {
          manager.getDynamicPropertiesStore().saveAllowTvmTransferTrc10(Long.parseLong(entry.getValue()));
          break;
        }
        case (19): {
          manager.getDynamicPropertiesStore().saveTotalEnergyLimit2(Long.parseLong(entry.getValue()));
          break;
        }
        case (20): {
          if (manager.getDynamicPropertiesStore().getAllowMultiSign() == 0) {
            manager.getDynamicPropertiesStore().saveAllowMultiSign(Long.parseLong(entry.getValue()));
          }
          break;
        }
        case (21): {
          if (manager.getDynamicPropertiesStore().getAllowAdaptiveEnergy() == 0) {
            manager.getDynamicPropertiesStore().saveAllowAdaptiveEnergy(Long.parseLong(entry.getValue()));
          }
          break;
        }
        case (22): {
          manager.getDynamicPropertiesStore().saveUpdateAccountPermissionFee(Long.parseLong(entry.getValue()));
        }
        case (23): {
          manager.getDynamicPropertiesStore().saveMultiSignFee(Long.parseLong(entry.getValue()));
        }
        case (24): {
          manager.getDynamicPropertiesStore().saveIncreaseSupplyAddress(ByteArray.fromHexString(entry.getValue()));
          break;
        }
        case (25): {
          manager.getDynamicPropertiesStore().saveDecreaseSupplyAddress(ByteArray.fromHexString(entry.getValue()));
          break;
        }
        default:
          break;
      }
    }
  }


}
