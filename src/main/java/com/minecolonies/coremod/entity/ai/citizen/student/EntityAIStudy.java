package com.minecolonies.coremod.entity.ai.citizen.student;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.util.StudyItem;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingLibrary;
import com.minecolonies.coremod.colony.jobs.JobStudent;
import com.minecolonies.coremod.entity.ai.basic.AbstractEntityAISkill;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;

/**
 * The Entity AI study class.
 */
public class EntityAIStudy extends AbstractEntityAISkill<JobStudent>
{
    /**
     * Delay for each subject study.
     */
    private static final int STUDY_DELAY = 20 * 60;

    /**
     * The current pos to study at.
     */
    private BlockPos studyPos = null;

    /**
     * Constructor for the student.
     * Defines the tasks the student executes.
     *
     * @param job a student job to use.
     */
    public EntityAIStudy(@NotNull final JobStudent job)
    {
        super(job);
        super.registerTargets(
          new AITarget(IDLE, START_WORKING, 1),
          new AITarget(START_WORKING, this::startWorkingAtOwnBuilding, TICKS_SECOND),
          new AITarget(STUDY, this::study, STANDARD_DELAY)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<? extends BuildingLibrary> getExpectedBuildingClass()
    {
        return BuildingLibrary.class;
    }

    /**
     * The AI task for the student to study.
     * For this he should walk between the different bookcase hit them once and then stand around for a while.
     *
     * @return the next IAIState.
     */
    private IAIState study()
    {
        final ICitizenData data = worker.getCitizenData();

        if (studyPos == null)
        {
            studyPos = getOwnBuilding(BuildingLibrary.class).getRandomBookShelf();
        }

        if (walkToBlock(studyPos))
        {
            setDelay(WALK_DELAY);
            return getState();
        }

        // Search for Items to use to study
        final List<StudyItem> currentItems = new ArrayList<>();
        worker.decreaseSaturationForAction();

        for (final StudyItem curItem : getOwnBuilding(BuildingLibrary.class).getStudyItems())
        {
            final int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(worker,
              itemStack -> !ItemStackUtils.isEmpty(itemStack) && itemStack.getItem() == curItem.getItem());

            if (slot != -1)
            {
                curItem.setSlot(slot);
                currentItems.add(curItem);
            }
        }

        // Create a new Request for items
        if (currentItems.isEmpty())
        {
            // Default levelup
            data.getCitizenSkillHandler().tryLevelUpIntelligence(world.rand, 50, data);
            worker.setHeldItem(Hand.MAIN_HAND, ItemStackUtils.EMPTY);

            for (final StudyItem studyItem : getOwnBuilding(BuildingLibrary.class).getStudyItems())
            {
                final int bSlot = InventoryUtils.findFirstSlotInProviderWith(getOwnBuilding(), studyItem.getItem());
                if (bSlot > -1)
                {
                    if (walkToBuilding())
                    {
                        setDelay(WALK_DELAY);
                        return getState();
                    }
                    takeItemStackFromProvider(getOwnBuilding(), bSlot);
                }
                else
                {
                    checkIfRequestForItemExistOrCreateAsynch(new ItemStack(studyItem.getItem(), studyItem.getBreakPct() / 10 > 0 ? studyItem.getBreakPct() / 10 : 1));
                }
            }
        }
        // Use random item
        else
        {
            final StudyItem chosenItem = currentItems.get(world.rand.nextInt(currentItems.size()));

            worker.setHeldItem(Hand.MAIN_HAND, new ItemStack(chosenItem.getItem(), 1));
            data.getCitizenSkillHandler().tryLevelUpIntelligence(world.rand, 50 * 100 / chosenItem.getSkillIncreasePct(), data);
            // Break item rand
            if (world.rand.nextInt(100) <= chosenItem.getBreakPct())
            {
                data.getInventory().extractItem(chosenItem.getSlot(), 1, false);
            }
        }

        studyPos = null;
        setDelay(STUDY_DELAY);
        return getState();
    }

    /**
     * Redirects the student to his library.
     *
     * @return the next state.
     */
    private IAIState startWorkingAtOwnBuilding()
    {
        if (walkToBuilding())
        {
            return getState();
        }
        return STUDY;
    }
}
