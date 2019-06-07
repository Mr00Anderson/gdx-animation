package me.winter.gdx.animation;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.OrderedMap;
import me.winter.gdx.animation.math.Curve;

import java.util.function.Consumer;

/**
 * Represents an animation of a Spriter SCML file. An animation holds {@link Timeline}s and a {@link Mainline} to
 * animate objects. Furthermore it holds a {@link #length}, a {@link #name} and whether it is {@link
 * #looping} or not.
 *
 * @author Alexander Winter
 */
public class Animation
{
	private final String name;
	private final int length; // millis
	private boolean looping;

	private final Mainline mainline;
	private final Array<Timeline> timelines;

	private final Array<AnimatedPart> tweenedObjects; //sprites made on runtime by tweening original sprites from animation
	private final OrderedMap<Integer, Sprite> sprites;

	private final ObjectMap<String, Consumer<AnimatedPart>> transformations = new ObjectMap<>();

	/**
	 * Milliseconds
	 */
	private float time = 0;
	private float speed = 1f, alpha = 1f;

	private AnimatedPart root = new AnimatedPart();

	public Animation(String name, int length, boolean looping, Mainline mainline, Array<Timeline> timelines)
	{
		this.name = name;

		this.length = length;
		this.looping = looping;

		this.mainline = mainline;
		this.timelines = timelines;

		tweenedObjects = new Array<>();
		tweenedObjects.setSize(timelines.size);
		sprites = new OrderedMap<>();

		for(Timeline timeline : timelines)
		{
			if(timeline instanceof SpriteTimeline)
			{
				Sprite sprite = new Sprite();
				tweenedObjects.set(timeline.getId(), sprite);
				sprites.put(((SpriteTimeline)timeline).getZIndex(), sprite);
			}
			else
				tweenedObjects.set(timeline.getId(), new AnimatedPart());
		}

		sprites.orderedKeys().sort();
	}

	public Animation(Animation animation)
	{
		this(animation.name,
				animation.length,
				animation.looping,
				new Mainline(animation.mainline),
				Timeline.clone(animation.timelines));
	}

	public void draw(Batch batch)
	{
		float prevColor = batch.getPackedColor();
		Color tmp = batch.getColor();
		tmp.a *= alpha;
		batch.setColor(tmp);

		for(Sprite sprite : sprites.values())
			sprite.draw(batch);

		batch.setColor(prevColor);
	}

	/**
	 * Updates this player. This means the current time gets increased by {@link #speed} and is applied to the current
	 * animation.
	 *
	 * @param delta time in milliseconds
	 */
	public void update(float delta)
	{
		setTime(time + speed * delta);

		MainlineKey currentKey = mainline.getKeyBeforeTime((int)time, looping);

		for(ObjectRef ref : currentKey.objectRefs)
			update(currentKey, ref, (int)time);
	}

	protected void update(MainlineKey currentKey, ObjectRef ref, int time)
	{
		//Get the timelines, the ref's pointing to
		Timeline timeline = timelines.get(ref.timeline);
		AnimatedPart tweened = tweenedObjects.get(ref.timeline);

		TimelineKey key = timeline.getKeys().get(ref.key); //get the last previous key

		TimelineKey nextKey;
		int timeOfNext;

		if(ref.key + 1 == timeline.getKeys().size)
		{
			if(!looping)
			{
				//no need to tween, stay freezed at first sprite
				AnimatedPart tweenTarget = tweenedObjects.get(ref.timeline);

				tweenTarget.set(key.getObject());

				AnimatedPart parent = ref.parent != null ? tweenedObjects.get(ref.parent.timeline) : root;
				tweenTarget.unmap(parent);
				return;
			}

			nextKey = timeline.getKeys().get(0);
			timeOfNext = nextKey.getTime() + length; //wrap around
		}
		else
		{
			nextKey = timeline.getKeys().get(ref.key + 1);
			timeOfNext = nextKey.getTime();
		}

		float timeDiff = timeOfNext - key.getTime();
		float timeRatio = currentKey.curve.interpolate(0f, 1f, (time - key.getTime()) / timeDiff);


		//Tween object
		AnimatedPart obj1 = key.getObject();
		AnimatedPart obj2 = nextKey.getObject();

		Curve curve = key.getCurve();

		tweened.setAngle(curve.interpolateAngle(obj1.getAngle(), obj2.getAngle(), timeRatio, key.getSpin()));

		curve.interpolateVector(obj1.getPosition(), obj2.getPosition(), timeRatio, tweened.getPosition());
		curve.interpolateVector(obj1.getScale(), obj2.getScale(), timeRatio, tweened.getScale());

		if(timeline instanceof SpriteTimeline)
		{
			((Sprite)tweened).setAlpha(curve.interpolate(((Sprite)obj1).getAlpha(), ((Sprite)obj2).getAlpha(), timeRatio));
			((Sprite)tweened).setDrawable(((Sprite)obj1).getDrawable());
		}

		Consumer<AnimatedPart> transform = transformations.get(timeline.getName());

		if(transform != null)
			transform.accept(tweened);

		tweened.unmap(ref.parent != null ? tweenedObjects.get(ref.parent.timeline) : root);
	}

	public void reset()
	{
		time = 0;
		update(0);
	}

	public AnimatedPart getRoot()
	{
		return root;
	}

	public Array<AnimatedPart> getParts()
	{
		return tweenedObjects;
	}

	public ObjectMap<String, Consumer<AnimatedPart>> getTransformations()
	{
		return transformations;
	}

	public Array<Timeline> getTimelines()
	{
		return timelines;
	}

	public String getName()
	{
		return name;
	}

	/**
	 * Time is in milliseconds
	 *
	 * @return current time of this animation
	 */
	public float getTime()
	{
		return time;
	}

	public void setTime(float time)
	{
		if(looping)
			while(time < 0)
				time += length;
		else if(time < 0)
			time = 0;

		if(looping)
			while(time >= length)
				time -= length;
		else if(time > length)
			time = length;

		this.time = time;
	}

	public float getSpeed()
	{
		return speed;
	}

	public void setSpeed(float speed)
	{
		this.speed = speed;
	}

	public float getAlpha()
	{
		return alpha;
	}

	public void setAlpha(float alpha)
	{
		this.alpha = alpha;
	}

	public int getLength()
	{
		return length;
	}

	public boolean isLooping()
	{
		return looping;
	}

	public void setLooping(boolean looping)
	{
		this.looping = looping;
	}

	public boolean isDone()
	{
		return time == length;
	}
}
